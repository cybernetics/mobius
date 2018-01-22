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
package com.spotify.mobius.rx2;

import com.spotify.mobius.rx2.RxMobius.SubtypeEffectHandlerBuilder;
import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.Scheduler;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import javax.annotation.Nullable;

/**
 * An {@link ObservableTransformer} factory to that creates transformers from {@link Action} and
 * {@link Consumer}. These transformers are useful when performing effects that do not result in
 * events.
 */
class Transformers {

  private Transformers() {}

  /**
   * Creates an {@link ObservableTransformer} that will flatten the provided {@link Action} into the
   * stream as a {@link Completable} every time it receives an effect from the upstream effects
   * observable. This will result in calling the provided Action every time an effect is dispatched
   * to the created effect transformer.
   *
   * @param doEffect {@link Action} to be run every time the effect is requested
   * @param <F> the type of Effect this transformer handles
   * @param <E> these transformers are for effects that do not result in any events; however, they
   *     still need to share the same Event type
   * @return an {@link ObservableTransformer} that can be used with a {@link
   *     SubtypeEffectHandlerBuilder}.
   */
  static <F, E> ObservableTransformer<F, E> fromAction(final Action doEffect) {
    return fromAction(doEffect, null);
  }

  /**
   * Creates an {@link ObservableTransformer} that will flatten the provided {@link Action} into the
   * stream as a {@link Completable} every time it receives an effect from the upstream effects
   * observable. This Completable will be subscribed on the specified {@link Scheduler}. This will
   * result in calling the provided Action on the specified scheduler every time an effect is
   * dispatched to the created effect transformer.
   *
   * @param doEffect {@link Action} to be run every time the effect is requested
   * @param scheduler the {@link Scheduler} that the action should be run on
   * @param <F> the type of Effect this transformer handles
   * @param <E> these transformers are for effects that do not result in any events; however, they
   *     still need to share the same Event type
   * @return an {@link ObservableTransformer} that can be used with a {@link
   *     SubtypeEffectHandlerBuilder}.
   */
  static <F, E> ObservableTransformer<F, E> fromAction(
      final Action doEffect, @Nullable final Scheduler scheduler) {
    return new ObservableTransformer<F, E>() {
      @Override
      public ObservableSource<E> apply(Observable<F> effectStream) {
        return effectStream
            .flatMapCompletable(
                new Function<F, CompletableSource>() {
                  @Override
                  public CompletableSource apply(F f) throws Exception {
                    return scheduler == null
                        ? Completable.fromAction(doEffect)
                        : Completable.fromAction(doEffect).subscribeOn(scheduler);
                  }
                })
            .toObservable();
      }
    };
  }

  /**
   * Creates an {@link ObservableTransformer} that will flatten the provided {@link Consumer} into
   * the stream as a {@link Completable} every time it receives an effect from the upstream effects
   * observable. This will result in calling the consumer and and passing it the requested effect
   * object.
   *
   * @param doEffect {@link Consumer} to be run every time the effect is requested
   * @param <F> the type of Effect this transformer handles
   * @param <E> these transformers are for effects that do not result in any events; however, they
   *     still need to share the same Event type
   * @return an {@link ObservableTransformer} that can be used with a {@link
   *     SubtypeEffectHandlerBuilder}.
   */
  static <F, E> ObservableTransformer<F, E> fromConsumer(final Consumer<F> doEffect) {
    return fromConsumer(doEffect, null);
  }

  /**
   * Creates an {@link ObservableTransformer} that will flatten the provided {@link Consumer} into
   * the stream as a {@link Completable} every time it receives an effect from the upstream effects
   * observable. This will result in calling the consumer on the specified scheduler, and passing it
   * the requested effect object.
   *
   * @param doEffect {@link Consumer} to be run every time the effect is requested
   * @param <F> the type of Effect this transformer handles
   * @param <E> these transformers are for effects that do not result in any events; however, they
   *     still need to share the same Event type
   * @return an {@link ObservableTransformer} that can be used with a {@link
   *     SubtypeEffectHandlerBuilder}.
   */
  static <F, E> ObservableTransformer<F, E> fromConsumer(
      final Consumer<F> doEffect, @Nullable final Scheduler scheduler) {
    return new ObservableTransformer<F, E>() {
      @Override
      public ObservableSource<E> apply(Observable<F> effectStream) {
        return effectStream
            .flatMapCompletable(
                new Function<F, CompletableSource>() {
                  @Override
                  public CompletableSource apply(final F effect) throws Exception {
                    Completable completable =
                        Completable.fromAction(
                            new Action() {
                              @Override
                              public void run() throws Exception {
                                doEffect.accept(effect);
                              }
                            });
                    return scheduler == null ? completable : completable.subscribeOn(scheduler);
                  }
                })
            .toObservable();
      }
    };
  }
}
