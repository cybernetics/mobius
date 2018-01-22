/*
 * -\-\-
 * Mobius
 * --
 * Copyright (c) 2017-2018 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package com.spotify.mobius.android;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import com.spotify.mobius.Connectable;
import com.spotify.mobius.Connection;
import com.spotify.mobius.First;
import com.spotify.mobius.Mobius;
import com.spotify.mobius.Next;
import com.spotify.mobius.functions.Consumer;
import com.spotify.mobius.runners.ImmediateWorkRunner;
import com.spotify.mobius.runners.WorkRunner;
import com.spotify.mobius.runners.WorkRunners;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public class MobiusAndroidControllerTest {

  private static final Connectable<String, String> effectHandler =
      eventConsumer ->
          new Connection<String>() {
            @Override
            public void accept(String value) {}

            @Override
            public void dispose() {}
          };

  private static Connectable<String, String> view() {
    return (eventConsumer) ->
        new Connection<String>() {
          @Override
          public void accept(String value) {}

          @Override
          public void dispose() {}
        };
  }

  public static class Lifecycle {

    private final MobiusAndroidController<String, String, String> underTest =
        new MobiusAndroidController<>(
            Mobius.<String, String, String>loop(
                    (model, event) -> Next.next(model + event), effectHandler)
                .eventRunner(WorkRunners::immediate)
                .effectRunner(WorkRunners::immediate),
            new ModelSaveRestore<String>() {
              @Nonnull
              @Override
              public String getDefaultModel() {
                return "init";
              }

              @Override
              public void saveModel(String model, Bundle out) {}

              @Override
              public String restoreModel(Bundle in) {
                return "";
              }
            },
            WorkRunners.immediate());

    @Test
    public void canCreateView() throws Exception {
      underTest.connect(view());
    }

    @Test
    public void canStart() throws Exception {
      underTest.connect(view());
      underTest.start();
    }

    @Test
    public void canStop() throws Exception {
      underTest.connect(view());
      underTest.start();
      underTest.stop();
    }

    @Test
    public void canDestroyView() throws Exception {
      underTest.connect(view());
      underTest.start();
      underTest.stop();
      underTest.disconnect();
    }

    @Test
    public void canRestartAfterStopping() throws Exception {
      underTest.connect(view());
      underTest.start();
      underTest.stop();
      underTest.start();
    }

    @Test
    public void canDestroyEvenIfNeverStarted() throws Exception {
      underTest.connect(view());
      underTest.disconnect();
    }

    @Test
    public void cannotStartWithoutViewCreated() throws Exception {
      assertThatThrownBy(() -> underTest.start())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("init");
    }

    @Test
    public void cannotDestroyWhenRunning() throws Exception {
      underTest.connect(view());
      underTest.start();

      assertThatThrownBy(() -> underTest.disconnect())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("running");
    }

    @Test
    public void cannotStopBeforeCreating() throws Exception {
      assertThatThrownBy(() -> underTest.stop())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("init");
    }

    @Test
    public void cannotStopBeforeStarting() throws Exception {
      underTest.connect(view());

      assertThatThrownBy(() -> underTest.stop())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("created");
    }

    @Test
    public void cannotCreateTwice() throws Exception {
      underTest.connect(view());

      assertThatThrownBy(() -> underTest.connect(view()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("created");
    }

    @Test
    public void cannotStartTwice() throws Exception {
      underTest.connect(view());
      underTest.start();

      assertThatThrownBy(() -> underTest.start())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("running");
    }

    @Test
    public void cannotStopTwice() throws Exception {
      underTest.connect(view());
      underTest.start();
      underTest.stop();

      assertThatThrownBy(() -> underTest.stop())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("created");
    }

    @Test
    public void cannotDestroyTwice() throws Exception {
      underTest.connect(view());
      underTest.disconnect();

      assertThatThrownBy(() -> underTest.disconnect())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("init");
    }
  }

  public static class StateSaveRestore {

    private final MobiusAndroidController<String, String, String> underTest =
        new MobiusAndroidController<>(
            Mobius.<String, String, String>loop(
                    (model, event) -> Next.next(model + event), effectHandler)
                .eventRunner(WorkRunners::immediate)
                .effectRunner(WorkRunners::immediate),
            new ModelSaveRestore<String>() {
              @Nonnull
              @Override
              public String getDefaultModel() {
                return "init";
              }

              @Override
              public void saveModel(String model, Bundle out) {
                out.putString("key", "saved");
              }

              @Override
              public String restoreModel(Bundle in) {
                return in.getString("key");
              }
            },
            WorkRunners.immediate());

    @Test
    public void canSaveState() throws Exception {
      Bundle bundle = mock(Bundle.class);

      underTest.connect(view());
      underTest.start();
      underTest.stop();
      underTest.saveState(bundle);

      verify(bundle).putString(anyString(), anyString());
    }

    @Test
    public void canRestoreState() throws Exception {
      Bundle bundle = mock(Bundle.class);
      when(bundle.getString(anyString())).thenReturn("restored");

      underTest.restoreState(bundle);

      verify(bundle).getString(anyString());
    }

    @Test
    public void canSaveStateAfterCreating() throws Exception {
      Bundle bundle = mock(Bundle.class);

      underTest.connect(view());
      underTest.saveState(bundle);

      verify(bundle).putString(anyString(), anyString());
    }

    @Test
    public void canRestoreStateAfterCreating() throws Exception {
      Bundle bundle = mock(Bundle.class);
      when(bundle.getString(anyString())).thenReturn("restored");

      underTest.connect(view());
      underTest.restoreState(bundle);

      verify(bundle).getString(anyString());
    }

    @Test
    public void cannotRestoreStateAfterStarting() throws Exception {
      Bundle bundle = mock(Bundle.class);

      underTest.connect(view());
      underTest.start();

      assertThatThrownBy(() -> underTest.restoreState(bundle))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("running");
    }

    @Test
    public void cannotSaveStateAfterStarting() throws Exception {
      Bundle bundle = mock(Bundle.class);

      underTest.connect(view());
      underTest.start();

      assertThatThrownBy(() -> underTest.saveState(bundle))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("running");
    }

    @Test
    public void canSaveStateAfterStopping() throws Exception {
      Bundle bundle = mock(Bundle.class);

      underTest.connect(view());
      underTest.start();
      underTest.stop();
      underTest.saveState(bundle);

      verify(bundle).putString(anyString(), anyString());
    }

    @Test
    public void canRestoreStateAfterStopping() throws Exception {
      Bundle bundle = mock(Bundle.class);
      when(bundle.getString(anyString())).thenReturn("restored");

      underTest.connect(view());
      underTest.start();
      underTest.stop();
      underTest.restoreState(bundle);

      verify(bundle).getString(anyString());
    }

    @Test
    public void canCallSaveWithNullBundle() throws Exception {
      underTest.saveState(null);
    }

    @Test
    public void canCallRestoreWithNullBundle() throws Exception {
      underTest.restoreState(null);
    }
  }

  public static class Loop {

    private final MobiusAndroidController<String, String, String> underTest =
        new MobiusAndroidController<>(
            Mobius.<String, String, String>loop(
                    (model, event) -> Next.next(model + event), effectHandler)
                .eventRunner(WorkRunners::immediate)
                .effectRunner(WorkRunners::immediate)
                .init(First::first),
            new ModelSaveRestore<String>() {
              @Nonnull
              @Override
              public String getDefaultModel() {
                return "init";
              }

              @Override
              public void saveModel(String model, Bundle out) {}

              @Override
              public String restoreModel(Bundle in) {
                return "restored";
              }
            },
            WorkRunners.immediate());

    @Test
    public void startsFromDefaultModel() throws Exception {
      @SuppressWarnings("unchecked")
      Connection<String> renderer = mock(Connection.class);

      underTest.connect(eventConsumer -> renderer);
      underTest.start();

      verify(renderer).accept("init");
    }

    @Test
    public void restoringStartsFromRestoredModel() throws Exception {
      @SuppressWarnings("unchecked")
      Connection<String> renderer = mock(Connection.class);

      underTest.restoreState(mock(Bundle.class));
      underTest.connect(eventConsumer -> renderer);
      underTest.start();

      verify(renderer).accept("restored");
    }

    @Test
    public void resumingStartsFromMostRecentModel() throws Exception {
      @SuppressWarnings("unchecked")
      Connection<String> renderer = mock(Connection.class);

      AtomicReference<Consumer<String>> consumer = new AtomicReference<>();

      underTest.connect(
          eventConsumer -> {
            consumer.set(eventConsumer);
            return renderer;
          });

      underTest.start();
      consumer.get().accept("!");

      verify(renderer).accept("init!");

      underTest.stop();
      reset(renderer);
      underTest.start();

      verify(renderer).accept("init!");
    }
  }

  public static class Connect {
    private final MobiusAndroidController<String, String, String> underTest =
        new MobiusAndroidController<>(
            Mobius.<String, String, String>loop(
                    (model, event) -> Next.next(model + event), effectHandler)
                .eventRunner(WorkRunners::immediate)
                .effectRunner(WorkRunners::immediate)
                .init(First::first),
            new ModelSaveRestore<String>() {
              @Nonnull
              @Override
              public String getDefaultModel() {
                return "init";
              }

              @Override
              public void saveModel(String model, Bundle out) {
                out.putString("key", "saved");
              }

              @Override
              public String restoreModel(Bundle in) {
                return in.getString("key");
              }
            },
            WorkRunners.immediate());

    @Test
    public void modelHandlerMustReturnConsumer() throws Exception {
      assertThatThrownBy(
              () -> {
                //noinspection ConstantConditions
                underTest.connect(eventConsumer -> null);
              })
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void eventConsumerIsDisabledAfterDisconnect() throws Exception {
      AtomicReference<Consumer<String>> consumer = new AtomicReference<>();

      final AtomicBoolean disposed = new AtomicBoolean();

      @SuppressWarnings("unchecked")
      final Connection<String> renderer =
          spy(
              new Connection<String>() {
                @Override
                public void dispose() {
                  disposed.set(true);
                }

                @Override
                public void accept(String value) {}
              });

      underTest.connect(
          eventConsumer -> {
            consumer.set(eventConsumer);
            return renderer;
          });

      consumer.get().accept("1");
      underTest.start();
      consumer.get().accept("2");
      underTest.stop();
      consumer.get().accept("3");

      assertFalse(disposed.get());
      underTest.disconnect();
      assertTrue(disposed.get());

      consumer.get().accept("4");

      InOrder inOrder = inOrder(renderer);
      inOrder.verify(renderer).accept("init");
      inOrder.verify(renderer).accept("init2");
      inOrder.verify(renderer).dispose();
      inOrder.verifyNoMoreInteractions();
    }
  }

  public static class EventsAndUpdates {
    private final WorkRunner mainThreadRunner = new ImmediateWorkRunner();

    private MobiusAndroidController<String, String, String> underTest;

    @Before
    public void setUp() throws Exception {
      underTest = createWithWorkRunner(mainThreadRunner);
    }

    private MobiusAndroidController<String, String, String> createWithWorkRunner(
        WorkRunner mainThreadRunner) {
      return new MobiusAndroidController<>(
          Mobius.<String, String, String>loop(
                  (model, event) -> Next.next(model + event), effectHandler)
              .eventRunner(WorkRunners::immediate)
              .effectRunner(WorkRunners::immediate),
          new ModelSaveRestore<String>() {
            @Nonnull
            @Override
            public String getDefaultModel() {
              return "init";
            }

            @Override
            public void saveModel(String model, Bundle out) {
              out.putString("key", "saved");
            }

            @Override
            public String restoreModel(Bundle in) {
              return in.getString("key");
            }
          },
          mainThreadRunner);
    }

    @Test
    public void updaterCanReceiveViewUpdates() throws Exception {
      @SuppressWarnings("unchecked")
      Connection<String> renderer = mock(Connection.class);

      AtomicReference<Consumer<String>> consumer = new AtomicReference<>();

      underTest.connect(
          eventConsumer -> {
            consumer.set(eventConsumer);
            return renderer;
          });

      underTest.start();
      consumer.get().accept("!");

      verify(renderer).accept("init!");
    }

    @Test
    public void updaterReceivesViewUpdatesOnMainThread() throws Exception {
      KnownThreadWorkRunner mainThreadRunner = new KnownThreadWorkRunner();
      final AtomicReference<Thread> actualThread = new AtomicReference<>();
      final Semaphore rendererGotModel = new Semaphore(0);

      @SuppressWarnings("unchecked")
      Connection<String> renderer =
          new Connection<String>() {
            @Override
            public void accept(String value) {
              actualThread.set(Thread.currentThread());
              rendererGotModel.release();
            }

            @Override
            public void dispose() {}
          };

      underTest = createWithWorkRunner(mainThreadRunner);

      underTest.connect(
          eventConsumer -> {
            return renderer;
          });

      underTest.start();

      rendererGotModel.tryAcquire(5, TimeUnit.SECONDS);

      assertThat(actualThread.get(), is(mainThreadRunner.workerThread));
    }

    @Test
    public void viewdataUpdaterMapsData() throws Exception {
      @SuppressWarnings("unchecked")
      Connection<String> renderer = mock(Connection.class);

      MobiusController<String, String> mappedUnderTest =
          new MappingMobiusController<>(underTest, model -> "mapped");

      AtomicReference<Consumer<String>> consumer = new AtomicReference<>();

      mappedUnderTest.connect(
          eventConsumer -> {
            consumer.set(eventConsumer);
            return renderer;
          });
      mappedUnderTest.start();

      verify(renderer).accept("mapped");
    }

    @Test
    public void eventsWhenNotRunningAreDropped() throws Exception {
      @SuppressWarnings("unchecked")
      Connection<String> renderer = mock(Connection.class);

      AtomicReference<Consumer<String>> consumer = new AtomicReference<>();

      underTest.connect(
          eventConsumer -> {
            consumer.set(eventConsumer);
            return renderer;
          });

      consumer.get().accept("!");

      underTest.start();

      verify(renderer, never()).accept("init!");
    }
  }

  private static class KnownThreadWorkRunner implements WorkRunner {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private volatile Thread workerThread = null;

    @Override
    public void post(final Runnable runnable) {
      executorService.submit(
          new Runnable() {
            @Override
            public void run() {
              workerThread = Thread.currentThread();
              runnable.run();
            }
          });
    }

    @Override
    public void dispose() {
      executorService.shutdown();
    }
  }
}
