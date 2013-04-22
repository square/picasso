package com.squareup.picasso;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.widget.ImageView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;

import static com.squareup.picasso.Request.Type;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
  private static final File FILE_1 = new File("C:\\windows\\system32\\logo.exe");
  private static final String FILE_1_URL = "file:///" + FILE_1.getPath();
  private static final String FILE_1_URL_NO_AUTHORITY = "file:/" + FILE_1.getParent();
  private static final String CONTENT_1_URL = "content://zip/zap/zoop.jpg";

  private static final Answer NO_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      return null;
    }
  };
  private static final Answer LOADER_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      return new Loader.Response(null, false);
    }
  };
  private static final Answer IO_EXCEPTION_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      throw new IOException();
    }
  };
  private static final Answer NULL_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      return null;
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

  private Context context;
  private SynchronousExecutorService executor;
  private Loader loader;
  private Cache cache;

  @Before public void setUp() {
    context = spy(new Activity());
    doReturn(context).when(context).getApplicationContext();

    executor = new SynchronousExecutorService();
    loader = mock(Loader.class);
    cache = mock(Cache.class);
  }

  @After public void tearDown() {
    executor.runnableList.clear();
  }

  @Test public void loadDoesNotAcceptInvalidInput() throws IOException {
    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    try {
      picasso.load((String) null);
      fail("Null URL should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load("");
      fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load("      ");
      fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load((File) null);
      fail("Null File should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load(0);
      fail("Null File should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void singleIsLazilyInitialized() throws Exception {
    assertThat(Picasso.singleton).isNull();
    Picasso.with(new Activity());
    assertThat(Picasso.singleton).isNotNull();
    Picasso.singleton = null;
  }

  @Test(expected = IllegalArgumentException.class)
  public void loadNullStringIsGuarded() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load((String) null).into(target);
  }

  @Test(expected = IllegalArgumentException.class)
  public void loadNullFileIsGuarded() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load((File) null).into(target);
  }

  @Test(expected = IllegalArgumentException.class)
  public void loadNoResourceIsGuarded() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(0).into(target);
  }

  @Test public void loadIntoImageView() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verify(target).setImageBitmap(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
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

  @Test public void fetchIntoStrongTarget() throws Exception {
    Target target = mock(Target.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).fetch(target);

    verifyZeroInteractions(target);
    executor.flush();
    verify(target).onSuccess(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadFileIntoImageView() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(FILE_1).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verifyZeroInteractions(loader);
    verify(target).setImageBitmap(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadFileIntoTarget() throws Exception {
    Target target = mock(Target.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(FILE_1).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verify(target).onSuccess(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadFileUrlIntoImageView() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(FILE_1_URL).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verifyZeroInteractions(loader);
    verify(target).setImageBitmap(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadFileUrlWithoutAuthorityIntoImageView() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(FILE_1_URL_NO_AUTHORITY).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verifyZeroInteractions(loader);
    verify(target).setImageBitmap(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadFileUrlIntoTarget() throws Exception {
    Target target = mock(Target.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(FILE_1_URL).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verify(target).onSuccess(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadContentUrlIntoImageView() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(CONTENT_1_URL).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verifyZeroInteractions(loader);
    verify(target).setImageBitmap(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadContentUrlIntoTarget() throws Exception {
    Target target = mock(Target.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(CONTENT_1_URL).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verify(target).onSuccess(bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadResourceIntoImageView() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(123).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verifyZeroInteractions(loader);
    verify(target).setImageBitmap(bitmap1);
  }

  @Test public void loadResourceIntoTarget() throws Exception {
    Target target = mock(Target.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(123).into(target);

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
    String key = Utils.createKey(URI_1, 0, null, null);

    executor.flush();
    verify(cache).set(key, bitmap1);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenDecoderReturnsNullDoesNotCallComplete() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, NULL_ANSWER);
    Request request =
        new Request(picasso, URI_1, 0, target, null, null, Request.Type.STREAM, false, 0,
            errorDrawable);
    request = spy(request);
    picasso.submit(request);
    executor.flush();

    verify(request, never()).complete();
    verify(request).error();
    verify(target).setImageDrawable(errorDrawable);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenFileDecoderReturnsNullDoesNotCallComplete() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(NULL_ANSWER, NULL_ANSWER);
    Request request =
        new Request(picasso, FILE_1_URL, 0, target, null, null, Type.FILE, false, 0, errorDrawable);
    request = spy(request);
    picasso.submit(request);
    executor.flush();

    verify(request, never()).complete();
    verify(request).error();
    verify(target).setImageDrawable(errorDrawable);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenContentResolverReturnsNullDoesNotCallComplete() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(NULL_ANSWER, NULL_ANSWER);
    Request request =
        new Request(picasso, CONTENT_1_URL, 0, target, null, null, Type.CONTENT, false, 0,
            errorDrawable);
    request = spy(request);
    picasso.submit(request);
    executor.flush();

    verify(request, never()).complete();
    verify(request).error();
    verify(target).setImageDrawable(errorDrawable);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewRetriesThreeTimesBeforeInvokingError() throws Exception {
    Picasso picasso = create(IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    ImageView target = mock(ImageView.class);

    Request request =
        new Request(picasso, URI_1, 0, target, null, null, Request.Type.STREAM, false, 0, null);
    request = spy(request);

    retryRequest(picasso, request);
    verify(picasso, times(3)).retry(request);
    verify(request).error();
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadFileIntoImageViewRetriesThreeTimesBeforeInvokingError() throws Exception {
    Picasso picasso = create(NULL_ANSWER, IO_EXCEPTION_ANSWER);
    ImageView target = mock(ImageView.class);

    Request request =
        new Request(picasso, FILE_1.getPath(), 0, target, null, null, Type.FILE, false, 0, null);
    request = spy(request);

    retryRequest(picasso, request);
    verify(picasso, times(3)).retry(request);
    verify(request).error();
    verifyZeroInteractions(loader);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadContentIntoImageViewRetriesThreeTimesBeforeInvokingError()
      throws Exception {
    Picasso picasso = create(NULL_ANSWER, IO_EXCEPTION_ANSWER);
    ImageView target = mock(ImageView.class);

    Request request = new Request(picasso, CONTENT_1_URL, 0, target, null,
        Collections.<Transformation>emptyList(), Type.CONTENT, false, 0, null);
    request = spy(request);

    retryRequest(picasso, request);
    verify(picasso, times(3)).retry(request);
    verify(request).error();
    verifyZeroInteractions(loader);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void withErrorDrawableAndFailsRequestSetsErrorDrawable() throws Exception {
    Picasso picasso = create(IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    ImageView target = mock(ImageView.class);

    Request request =
        new Request(picasso, URI_1, 0, target, null, null, Request.Type.STREAM, false, 0,
            errorDrawable);

    retryRequest(picasso, request);
    verify(target, never()).setImageBitmap(bitmap1);
    verify(target).setImageDrawable(errorDrawable);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenImageViewRequestFailsCleansUpTargetMap() throws Exception {
    Picasso picasso = create(IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    ImageView target = mock(ImageView.class);

    Request request =
        new Request(picasso, URI_1, 0, target, null, null, Request.Type.STREAM, false, 0, null);

    retryRequest(picasso, request);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewQuickCacheHit() throws Exception {
    // Assume bitmap is already in memory cache.
    String key = Utils.createKey(URI_1, 0, null, null);
    when(cache.get(key)).thenReturn(bitmap1);

    ImageView target = mock(ImageView.class);
    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).into(target);

    verify(picasso, never()).submit(any(Request.class));
    verify(target).setImageBitmap(bitmap1);
    assertThat(executor.runnableList).isEmpty();
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
    Picasso picasso = create(IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    Target target = mock(Target.class);

    Request request =
        new TargetRequest(picasso, URI_1, 0, target, false, null, null, Type.STREAM, false, 0, null);

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

  @Test public void doesNotDecodeAgainIfBitmapWithTransformationsAlreadyInCache() throws Exception {
    // Ensures that threads use the correct request key (including transformations) when looking up
    // an existing bitmap from cache.

    ImageView target1 = mock(ImageView.class);
    ImageView target2 = mock(ImageView.class);

    Transformation transformation = mock(Transformation.class);
    when(transformation.transform(any(Bitmap.class))).thenReturn(bitmap1);
    when(transformation.key()).thenReturn("transformation(something)");

    List<Transformation> transformations = new ArrayList<Transformation>(1);
    transformations.add(transformation);

    String key = Utils.createKey(URI_1, 0, null, transformations);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).transform(transformation).into(target1);
    picasso.load(URI_1).transform(transformation).into(target2);

    executor.executeFirst();
    when(cache.get(key)).thenReturn(bitmap1);
    executor.flush();

    verify(target1).setImageBitmap(bitmap1);
    verify(target2).setImageBitmap(bitmap1);
    verify(picasso.loader, times(1)).load(URI_1, false);

    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void withRecycledRetryRequestStopsRetrying() throws Exception {
    String key = Utils.createKey(URI_1, 0, null, null);
    when(cache.get(key)).thenReturn(bitmap1);

    ImageView target = mock(ImageView.class);

    Picasso picasso = create(IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
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

    List<Transformation> transformations = new ArrayList<Transformation>(1);
    Transformation resize = spy(new TestTransformation("test"));
    transformations.add(resize);

    Request request =
        new Request(picasso, URI_1, 0, target, null, transformations, Request.Type.STREAM, false, 0,
            null);
    picasso.submit(request);

    executor.flush();

    verify(resize).transform(bitmap1);

    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void reloadsTransformedBitmapFromCache() throws Exception {
    ImageView target = mock(ImageView.class);

    Transformation transformation = mock(Transformation.class);
    when(transformation.transform(any(Bitmap.class))).thenReturn(bitmap1);
    when(transformation.key()).thenReturn("transformation(something)");

    List<Transformation> transformations = new ArrayList<Transformation>(1);
    transformations.add(transformation);

    // Assume a transformed image is already in cache with matching key.
    when(cache.get(Utils.createKey(URI_1, 0, null, transformations))).thenReturn(bitmap1);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).transform(transformation).into(target);
    executor.flush();

    verify(loader, never()).load(URI_1, false);
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

    Request request =
        new Request(picasso, URI_1, 0, target, null, transformations, Request.Type.STREAM, false, 0,
            null);
    picasso.submit(request);

    executor.flush();

    InOrder inOrder = inOrder(rotate, scale, resize);
    inOrder.verify(rotate).transform(any(Bitmap.class));
    inOrder.verify(scale).transform(any(Bitmap.class));
    inOrder.verify(resize).transform(any(Bitmap.class));

    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenRequestSkipsCacheDoesNotCache() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).skipCache().into(target);
    executor.flush();

    verify(cache, never()).set(anyString(), any(Bitmap.class));
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void builderInvalidLoader() throws Exception {
    try {
      new Picasso.Builder(context).loader(null);
      fail("Null Loader should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).loader(loader).loader(loader);
      fail("Setting Loader twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidExecutor() throws Exception {
    try {
      new Picasso.Builder(context).executor(null);
      fail("Null Executor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).executor(executor).executor(executor);
      fail("Setting Executor twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidCache() throws Exception {
    try {
      new Picasso.Builder(context).memoryCache(null);
      fail("Null Cache should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).memoryCache(cache).memoryCache(cache);
      fail("Setting Cache twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderCreatesDefaults() throws Exception {
    Picasso p = new Picasso.Builder(context).build();
    assertThat(p.loader).isNotNull();
    assertThat(p.cache).isNotNull();
    assertThat(p.service).isNotNull();
  }

  @Test public void withNullTransformThrows() {
    Picasso picasso = Picasso.with(new Activity());

    Transformation okTransformation = mock(Transformation.class);
    when(okTransformation.transform(any(Bitmap.class))).thenReturn(mock(Bitmap.class));
    when(okTransformation.key()).thenReturn("ok()");

    Transformation nullTransformation = mock(Transformation.class);
    when(nullTransformation.transform(any(Bitmap.class))).thenReturn(null);
    when(nullTransformation.key()).thenReturn("null()");

    List<Transformation> transformations = new ArrayList<Transformation>();

    transformations.add(new TestTransformation("OK"));
    transformations.add(new TestTransformation("NULL", null));

    Request request =
        new Request(picasso, CONTENT_1_URL, 0, mock(ImageView.class), null, transformations,
            Type.CONTENT, false, 0, null);

    try {
      Picasso.transformResult(request, Bitmap.createBitmap(10, 10, null), 0);
      fail("Should throw a NullPointerException when a transformation returns null.");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).contains("after 1 previous transformation");
      assertThat(e.getMessage()).contains("NULL returned null");
    }
  }

  @Test(expected = IllegalStateException.class)
  public void notRecyclingTransformThrows() {
    Picasso picasso = Picasso.with(new Activity());

    Bitmap input = mock(Bitmap.class);
    Bitmap output = mock(Bitmap.class);
    Transformation badTransformation = mock(Transformation.class);
    when(badTransformation.transform(input)).thenReturn(output);
    when(input.isRecycled()).thenReturn(false);

    List<Transformation> transformations = new ArrayList<Transformation>();
    transformations.add(badTransformation);

    Request request =
        new Request(picasso, CONTENT_1_URL, 0, null, null, transformations, Type.CONTENT, false, 0,
            null);
    Picasso.transformResult(request, input, 0);
  }

  private void retryRequest(Picasso picasso, Request request) {
    picasso.submit(request);

    for (int i = Request.DEFAULT_RETRY_COUNT; i >= 0; i--) {
      executor.flush();
      runUiThreadTasksIncludingDelayedTasks();
    }
  }

  private Picasso create(Answer loaderAnswer, Answer decoderAnswer) throws IOException {
    Picasso picasso = new Picasso.Builder(context) //
        .loader(loader) //
        .executor(executor) //
        .memoryCache(cache) //
        .build();

    picasso = spy(picasso);

    doAnswer(loaderAnswer).when(loader).load(anyString(), anyBoolean());
    doAnswer(decoderAnswer).when(picasso).decodeFile(anyString(), any(PicassoBitmapOptions.class));
    doAnswer(decoderAnswer).when(picasso)
        .decodeContentStream(any(Uri.class), any(PicassoBitmapOptions.class));
    doAnswer(decoderAnswer).when(picasso)
        .decodeStream(any(InputStream.class), any(PicassoBitmapOptions.class));
    doAnswer(decoderAnswer).when(picasso)
        .decodeResource(any(Resources.class), anyInt(), any(PicassoBitmapOptions.class));
    return picasso;
  }
}
