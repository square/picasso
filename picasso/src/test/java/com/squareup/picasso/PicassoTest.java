package com.squareup.picasso;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;

import static android.graphics.BitmapFactory.Options;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.robolectric.Robolectric.runUiThreadTasksIncludingDelayedTasks;

@RunWith(PicassoTestRunner.class)
public class PicassoTest {

  private static final String URI_1 = "URI1";
  private static final String URI_2 = "URI2";
  private static final Answer NO_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      return null;
    }
  };
  private static final Answer LOADER_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      return new Loader.Response(null, false, false);
    }
  };
  private static final Answer LOADER_IO_EXCEPTION_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      throw new IOException();
    }
  };
  private static final Answer BITMAP1_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      return bitmap1;
    }
  };
  private static final Answer BITMAP2_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      return bitmap2;
    }
  };

  private static final Resources resources = Robolectric.application.getResources();

  private static final Bitmap placeHolder = Bitmap.createBitmap(5, 5, null);
  private static final Bitmap error = Bitmap.createBitmap(6, 6, null);
  private static final Bitmap bitmap1 = Bitmap.createBitmap(10, 10, null);
  private static final Bitmap bitmap2 = Bitmap.createBitmap(15, 15, null);
  private static final BitmapDrawable errorDrawable = new BitmapDrawable(resources, error);
  private static final BitmapDrawable placeholderDrawable =
      new BitmapDrawable(resources, placeHolder);

  private SynchronousExecutorService executor;
  private Loader loader;
  private Cache cache;

  @Before public void setUp() {
    executor = new SynchronousExecutorService();
    loader = mock(Loader.class);
    cache = mock(Cache.class);
  }

  @After public void tearDown() {
    executor.runnables.clear();
  }

  @Test public void singleIsLazilyInitialized() throws Exception {
    assertThat(Picasso.singleton).isNull();
    Picasso.with(new Activity());
    assertThat(Picasso.singleton).isNotNull();
    Picasso.singleton = null;
  }

  @Test public void loadIntoImageView() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verify(target).setImageBitmap(bitmap1);
  }

  @Test public void loadIntoTarget() throws Exception {
    Target target = mock(Target.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verify(target).onSuccess(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewWithPlaceHolderDrawable() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).placeholder(placeholderDrawable).into(target);

    verify(target).setImageDrawable(placeholderDrawable);
    executor.flush();
    verify(target).setImageBitmap(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewWithPlaceHolderResource() throws Exception {
    ImageView target = mock(ImageView.class);
    doAnswer(NO_ANSWER).when(target).setImageResource(anyInt());

    int placeholderResId = 42;

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).placeholder(placeholderResId).into(target);

    verify(target).setImageResource(placeholderResId);
    executor.flush();
    verify(target).setImageBitmap(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewCachesResult() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target);

    executor.flush();
    verify(cache).set(URI_1, bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoRetriesThreeTimesBeforeInvokingError() throws Exception {
    Picasso picasso = create(LOADER_IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    ImageView target = mock(ImageView.class);

    Request request =
        new Request(picasso, URI_1, target, null, Collections.<Transformation>emptyList(), null, 0,
            null);
    request = spy(request);

    retryRequest(picasso, request);
    verify(picasso, times(3)).retry(request);
    verify(request).error();
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void withErrorDrawableAndFailsRequestSetsErrorDrawable() throws Exception {
    Picasso picasso = create(LOADER_IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    ImageView target = mock(ImageView.class);

    Request request =
        new Request(picasso, URI_1, target, null, Collections.<Transformation>emptyList(), null, 0,
            errorDrawable);

    retryRequest(picasso, request);
    verify(target, never()).setImageBitmap(bitmap1);
    verify(target).setImageDrawable(errorDrawable);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenImageViewRequestFailsCleansUpTargetMap() throws Exception {
    Picasso picasso = create(LOADER_IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    ImageView target = mock(ImageView.class);

    Request request =
        new Request(picasso, URI_1, target, null, Collections.<Transformation>emptyList(), null, 0,
            null);

    retryRequest(picasso, request);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewQuickCacheHit() throws Exception {
    // Assume bitmap is already in memory cache.
    when(cache.get(URI_1)).thenReturn(bitmap1);

    ImageView target = mock(ImageView.class);
    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target);

    verify(picasso, never()).submit(any(Request.class));
    verify(target).setImageBitmap(bitmap1);
    assertThat(executor.runnables).isEmpty();
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenImageViewRequestCompletesCleansUpTargetMap() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target);

    assertThat(picasso.targetsToRequests.size()).isEqualTo(1);
    executor.flush();
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenTargetRequestFailsCleansUpTargetMap() throws Exception {
    Picasso picasso = create(LOADER_IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    Target target = mock(Target.class);

    Request request =
        new TargetRequest(picasso, URI_1, target, null, Collections.<Transformation>emptyList(),
            null, 0, null);

    retryRequest(picasso, request);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenTargetRequestCompletesCleansUpTargetMap() throws Exception {
    Target target = mock(Target.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target);

    assertThat(picasso.targetsToRequests.size()).isEqualTo(1);
    executor.flush();
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewWithDifferentUriRecyclesCorrectly() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP2_ANSWER);
    picasso.load(URI_1).into(target);
    picasso.load(URI_2).into(target);

    assertThat(picasso.targetsToRequests.size()).isEqualTo(1);
    executor.flush();
    verify(target, never()).setImageBitmap(bitmap1);
    verify(target).setImageBitmap(bitmap2);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void doesNotDecodeAgainIfBitmapAlreadyInCache() throws Exception {
    // Tests that if one thread decodes a bitmap and another one was waiting to start
    // it will instead pickup the bitmap from the cache instead of decoding it again.
    ImageView target1 = mock(ImageView.class);
    ImageView target2 = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target1);
    picasso.load(URI_1).into(target2);

    executor.executeFirst();
    when(cache.get(anyString())).thenReturn(bitmap1);
    executor.flush();

    verify(target1).setImageBitmap(bitmap1);
    verify(target2).setImageBitmap(bitmap1);
    verify(picasso.loader, times(1)).load(URI_1, false);

    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void withRecycledRetryRequestStopsRetrying() throws Exception {
    when(cache.get(URI_1)).thenReturn(bitmap1);

    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target);
    picasso.load(URI_2).into(target);
    executor.flush();
    picasso.load(URI_1).into(target);
    runUiThreadTasksIncludingDelayedTasks();

    verify(target, times(2)).setImageBitmap(bitmap1);
    verify(target, never()).setImageBitmap(bitmap2);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewWithTransformations() throws Exception {
    ImageView target = mock(ImageView.class);
    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);

    Bitmap transformationResult = mock(Bitmap.class);

    Transformation resize = mock(Transformation.class);
    when(resize.transform(any(Bitmap.class))).thenReturn(transformationResult);

    List<Transformation> transformations = new ArrayList<Transformation>(1);
    transformations.add(resize);

    Request request = new Request(picasso, URI_1, target, null, transformations, null, 0, null);
    picasso.submit(request);

    executor.flush();

    verify(resize).transform(bitmap1);

    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewWithMultipleTransformations() throws Exception {
    ImageView target = mock(ImageView.class);
    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);

    Bitmap transformationResult = mock(Bitmap.class);

    Transformation rotate = mock(Transformation.class);
    Transformation scale = mock(Transformation.class);
    Transformation resize = mock(Transformation.class);

    when(rotate.transform(any(Bitmap.class))).thenReturn(transformationResult);
    when(scale.transform(any(Bitmap.class))).thenReturn(transformationResult);
    when(resize.transform(any(Bitmap.class))).thenReturn(transformationResult);

    List<Transformation> transformations = new ArrayList<Transformation>(3);
    transformations.add(rotate);
    transformations.add(scale);
    transformations.add(resize);

    Request request = new Request(picasso, URI_1, target, null, transformations, null, 0, null);
    picasso.submit(request);

    executor.flush();

    InOrder inOrder = inOrder(rotate, scale, resize);
    inOrder.verify(rotate).transform(any(Bitmap.class));
    inOrder.verify(scale).transform(any(Bitmap.class));
    inOrder.verify(resize).transform(any(Bitmap.class));

    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void builderInvalidLoader() throws Exception {
    try {
      new Picasso.Builder().loader(null);
      fail("Null Loader should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder().loader(loader).loader(loader);
      fail("Setting Loader twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidExecutor() throws Exception {
    try {
      new Picasso.Builder().executor(null);
      fail("Null Executor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder().executor(executor).executor(executor);
      fail("Setting Executor twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidCache() throws Exception {
    try {
      new Picasso.Builder().memoryCache(null);
      fail("Null Cache should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder().memoryCache(cache).memoryCache(cache);
      fail("Setting Cache twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderMissingRequired() throws Exception {
    try {
      new Picasso.Builder().build();
      fail("Loader and executor are required.");
    } catch (IllegalStateException expected) {
    }
    try {
      new Picasso.Builder().loader(loader).build();
      fail("Loader and executor are required.");
    } catch (IllegalStateException expected) {
    }
    try {
      new Picasso.Builder().executor(executor).build();
      fail("Loader and executor are required.");
    } catch (IllegalStateException expected) {
    }
  }

  private void retryRequest(Picasso picasso, Request request) {
    picasso.submit(request);

    executor.flush();
    runUiThreadTasksIncludingDelayedTasks();
    executor.flush();
    runUiThreadTasksIncludingDelayedTasks();
    executor.flush();
    runUiThreadTasksIncludingDelayedTasks();
  }

  private Picasso create(Answer loaderAnswer, Answer decoderAnswer) throws Exception {
    Picasso picasso = new Picasso.Builder() //
        .loader(loader) //
        .executor(executor) //
        .memoryCache(cache) //
        .debug() //
        .build();

    picasso = spy(picasso);

    doAnswer(loaderAnswer).when(loader).load(anyString(), anyBoolean());
    doAnswer(decoderAnswer).when(picasso).decodeStream(any(InputStream.class), any(Options.class));
    return picasso;
  }

  @SuppressWarnings({ "NullableProblems", "SpellCheckingInspection" })
  private static class SynchronousExecutorService extends AbstractExecutorService {

    List<Runnable> runnables = new ArrayList<Runnable>();

    @Override public void shutdown() {
    }

    @Override public List<Runnable> shutdownNow() {
      return null;
    }

    @Override public boolean isShutdown() {
      return false;
    }

    @Override public boolean isTerminated() {
      return false;
    }

    @Override public boolean awaitTermination(long l, TimeUnit timeUnit)
        throws InterruptedException {
      return false;
    }

    @Override public void execute(Runnable runnable) {
      runnables.add(runnable);
    }

    public void flush() {
      for (Runnable runnable : runnables) {
        runnable.run();
      }
      runnables.clear();
    }

    public void executeFirst() {
      runnables.remove(0).run();
    }
  }
}
