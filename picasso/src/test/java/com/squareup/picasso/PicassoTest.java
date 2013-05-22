package com.squareup.picasso;

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
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.Picasso.Listener;
import static com.squareup.picasso.Request.Type;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.robolectric.Robolectric.pauseMainLooper;
import static org.robolectric.Robolectric.runUiThreadTasksIncludingDelayedTasks;
import static org.robolectric.Robolectric.unPauseMainLooper;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PicassoTest {
  private static final String URI_1 = "URI1";
  private static final String URI_2 = "URI2";
  private static final File FILE_1 = new File("C:\\windows\\system32\\logo.exe");
  private static final String FILE_1_URL = "file:///" + FILE_1.getPath();
  private static final String FILE_1_URL_NO_AUTHORITY = "file:/" + FILE_1.getParent();
  private static final String CONTENT_1_URL = "content://zip/zap/zoop.jpg";

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
  private static final Answer NPE_ANSWER = new Answer() {
    @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      throw new NullPointerException();
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

  private final Context context = Robolectric.application;
  private SynchronousExecutorService executor;
  private Loader loader;
  private Cache cache;
  private Stats stats;
  private Listener listener;

  @Before public void setUp() {
    executor = new SynchronousExecutorService();
    loader = mock(Loader.class);
    cache = mock(Cache.class);
    stats = mock(Stats.class);
    listener = mock(Listener.class);
  }

  @After public void tearDown() {
    executor.tasks.clear();
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
    Picasso.with(context);
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

    ArgumentCaptor<PicassoDrawable> captor = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(captor.capture());
    PicassoDrawable actualDrawable = captor.getValue();
    assertThat(actualDrawable.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);
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

    ArgumentCaptor<PicassoDrawable> captor = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(captor.capture());
    PicassoDrawable actualDrawable = captor.getValue();
    assertThat(actualDrawable.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

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

    ArgumentCaptor<PicassoDrawable> captor = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(captor.capture());
    PicassoDrawable actualDrawable = captor.getValue();
    assertThat(actualDrawable.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadFileUrlWithoutAuthorityIntoImageView() throws Exception {
    ImageView target = mock(ImageView.class);

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(FILE_1_URL_NO_AUTHORITY).into(target);

    verifyZeroInteractions(target);
    executor.flush();
    verifyZeroInteractions(loader);

    ArgumentCaptor<PicassoDrawable> captor = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(captor.capture());
    PicassoDrawable actualDrawable = captor.getValue();
    assertThat(actualDrawable.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

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

    ArgumentCaptor<PicassoDrawable> captor = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(captor.capture());
    PicassoDrawable actualDrawable = captor.getValue();
    assertThat(actualDrawable.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

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

    ArgumentCaptor<PicassoDrawable> captor = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(captor.capture());
    PicassoDrawable actualDrawable = captor.getValue();
    assertThat(actualDrawable.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);
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

    ArgumentCaptor<PicassoDrawable> placeholder = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(placeholder.capture());
    PicassoDrawable capturedPlaceholder = placeholder.getValue();
    assertThat(capturedPlaceholder.placeHolderDrawable).isSameAs(placeholderDrawable);
    reset(target);

    executor.flush();

    ArgumentCaptor<PicassoDrawable> actual = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(actual.capture());
    PicassoDrawable capturedActual = actual.getValue();
    assertThat(capturedActual.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewWithPlaceHolderResource() throws Exception {
    ImageView target = spy(new ImageView(context));
    doAnswer(NULL_ANSWER).when(target).setImageResource(anyInt());

    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    picasso.load(URI_1).placeholder(android.R.drawable.ic_delete).into(target);


    ArgumentCaptor<PicassoDrawable> actual = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(actual.capture());
    PicassoDrawable capturedActual = actual.getValue();
    assertThat(capturedActual.placeholderResId).isEqualTo(android.R.drawable.ic_delete);
    assertThat(capturedActual.bitmapDrawable).isNull();

    executor.flush();

    assertThat(capturedActual.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);
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
        new Request(picasso, URI_1, 0, target, null, null, Request.Type.NETWORK, false, false, 0,
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
        new Request(picasso, FILE_1_URL, 0, target, null, null, Type.FILE, false, false, 0,
            errorDrawable);
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
        new Request(picasso, CONTENT_1_URL, 0, target, null, null, Type.CONTENT, false, false, 0,
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
        new Request(picasso, URI_1, 0, target, null, null, Request.Type.NETWORK, false, false, 0,
            null);
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
        new Request(picasso, FILE_1.getPath(), 0, target, null, null, Type.FILE, false, false, 0,
            null);
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
        Collections.<Transformation>emptyList(), Type.CONTENT, false, false, 0, null);
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
        new Request(picasso, URI_1, 0, target, null, null, Request.Type.NETWORK, false, false, 0,
            errorDrawable);

    retryRequest(picasso, request);
    verify(target).setImageDrawable(errorDrawable);
    verifyNoMoreInteractions(target);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void whenImageViewRequestFailsCleansUpTargetMap() throws Exception {
    Picasso picasso = create(IO_EXCEPTION_ANSWER, BITMAP1_ANSWER);
    ImageView target = mock(ImageView.class);

    Request request =
        new Request(picasso, URI_1, 0, target, null, null, Request.Type.NETWORK, false, false, 0,
            null);

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

    ArgumentCaptor<PicassoDrawable> captor = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(captor.capture());
    PicassoDrawable actualDrawable = captor.getValue();
    assertThat(actualDrawable.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

    assertThat(executor.tasks).isEmpty();
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
    Request request =
        new TargetRequest(picasso, URI_1, 0, null, false, null, null, Type.NETWORK, false);

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

    ArgumentCaptor<PicassoDrawable> captor = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target).setImageDrawable(captor.capture());
    PicassoDrawable actualDrawable = captor.getValue();
    assertThat(actualDrawable.bitmapDrawable.getBitmap()).isEqualTo(bitmap2);

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

    ArgumentCaptor<PicassoDrawable> captor1 = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target1).setImageDrawable(captor1.capture());
    PicassoDrawable actualDrawable1 = captor1.getValue();
    assertThat(actualDrawable1.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

    ArgumentCaptor<PicassoDrawable> captor2 = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target2).setImageDrawable(captor2.capture());
    PicassoDrawable actualDrawable2 = captor2.getValue();
    assertThat(actualDrawable2.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

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

    ArgumentCaptor<PicassoDrawable> captor1 = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target1).setImageDrawable(captor1.capture());
    PicassoDrawable actualDrawable1 = captor1.getValue();
    assertThat(actualDrawable1.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

    ArgumentCaptor<PicassoDrawable> captor2 = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target2).setImageDrawable(captor2.capture());
    PicassoDrawable actualDrawable2 = captor2.getValue();
    assertThat(actualDrawable2.bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

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

    ArgumentCaptor<PicassoDrawable> captor = ArgumentCaptor.forClass(PicassoDrawable.class);
    verify(target, times(2)).setImageDrawable(captor.capture());
    List<PicassoDrawable> actualDrawable = captor.getAllValues();
    assertThat(actualDrawable).hasSize(2);
    assertThat(actualDrawable.get(0).bitmapDrawable.getBitmap()).isEqualTo(bitmap1);
    assertThat(actualDrawable.get(1).bitmapDrawable.getBitmap()).isEqualTo(bitmap1);

    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loadIntoImageViewWithTransformations() throws Exception {
    ImageView target = mock(ImageView.class);
    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);

    List<Transformation> transformations = new ArrayList<Transformation>(1);
    Transformation resize = spy(new TestTransformation("test"));
    transformations.add(resize);

    Request request =
        new Request(picasso, URI_1, 0, target, null, transformations, Request.Type.NETWORK, false,
            false, 0, null);
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
        new Request(picasso, URI_1, 0, target, null, transformations, Request.Type.NETWORK, false,
            false, 0, null);
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

  @Test public void builderInvalidListener() throws Exception {
    try {
      new Picasso.Builder(context).listener(null);
      fail("Null listener should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).listener(listener).listener(listener);
      fail("Setting Listener twice should throw exception.");
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
    Transformation okTransformation = mock(Transformation.class);
    when(okTransformation.transform(any(Bitmap.class))).thenReturn(mock(Bitmap.class));
    when(okTransformation.key()).thenReturn("ok()");

    Transformation nullTransformation = mock(Transformation.class);
    when(nullTransformation.transform(any(Bitmap.class))).thenReturn(null);
    when(nullTransformation.key()).thenReturn("null()");

    List<Transformation> transformations = new ArrayList<Transformation>();

    transformations.add(new TestTransformation("OK"));
    transformations.add(new TestTransformation("NULL", null));

    try {
      Picasso.applyCustomTransformations(transformations, Bitmap.createBitmap(10, 10, null));
      fail("Should throw a NullPointerException when a transformation returns null.");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).contains("after 1 previous transformation");
      assertThat(e.getMessage()).contains("NULL returned null");
    }
  }

  @Test(expected = IllegalStateException.class)
  public void notRecyclingTransformThrows() {
    Bitmap input = mock(Bitmap.class);
    Bitmap output = mock(Bitmap.class);
    Transformation badTransformation = mock(Transformation.class);
    when(badTransformation.transform(input)).thenReturn(output);
    when(input.isRecycled()).thenReturn(false);

    List<Transformation> transformations = new ArrayList<Transformation>();
    transformations.add(badTransformation);

    Picasso.applyCustomTransformations(transformations, input);
  }

  @Test(expected = IllegalStateException.class)
  public void recyclingTransformReturnsOriginalThrows() {
    Bitmap input = mock(Bitmap.class);

    Transformation badTransformation = mock(Transformation.class);
    when(badTransformation.transform(input)).thenReturn(input);
    when(input.isRecycled()).thenReturn(true);

    List<Transformation> transformations = new ArrayList<Transformation>();
    transformations.add(badTransformation);

    Picasso.applyCustomTransformations(transformations, input);
  }

  @Test public void cancelRequestBeforeExecution() throws Exception {
    Picasso picasso = create(NULL_ANSWER, NULL_ANSWER);
    ImageView target = mock(ImageView.class);
    Request request =
        new Request(picasso, null, 0, target, null, null, null, false, false, 0, null);
    picasso.submit(request);
    assertThat(picasso.targetsToRequests).hasSize(1);
    assertThat(request.future.isCancelled()).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetsToRequests).isEmpty();
    assertThat(request.future.isCancelled()).isTrue();
    verifyZeroInteractions(target);
  }

  @Test public void cancelTargetRequestBeforeExecution() throws Exception {
    Picasso picasso = create(NULL_ANSWER, NULL_ANSWER);
    Target target = mock(Target.class);
    Request request = new TargetRequest(picasso, null, 0, target, true, null, null, null, false);
    picasso.submit(request);
    assertThat(picasso.targetsToRequests).hasSize(1);
    assertThat(request.future.isCancelled()).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetsToRequests).isEmpty();
    assertThat(request.future.isCancelled()).isTrue();
    verifyZeroInteractions(target);
  }

  @Test public void cancelRequestBetweenRetries() throws Exception {
    Picasso picasso = create(IO_EXCEPTION_ANSWER, NULL_ANSWER);
    ImageView target = mock(ImageView.class);
    Request request =
        new Request(picasso, null, 0, target, null, null, Type.NETWORK, false, false, 0, null);
    picasso.submit(request);
    assertThat(picasso.targetsToRequests).hasSize(1);
    assertThat(request.future.isCancelled()).isFalse();
    executor.flush();
    runUiThreadTasksIncludingDelayedTasks();
    assertThat(picasso.targetsToRequests).hasSize(1);
    assertThat(request.future.isCancelled()).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetsToRequests).isEmpty();
    assertThat(request.future.isCancelled()).isTrue();
    verifyZeroInteractions(target);
  }

  @Test public void cancelRequestAfterResult() throws Exception {
    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    ImageView target = mock(ImageView.class);
    Request request =
        new Request(picasso, null, 0, target, null, null, Type.FILE, false, false, 0, null);
    picasso.submit(request);
    assertThat(picasso.targetsToRequests).hasSize(1);
    pauseMainLooper();
    executor.flush();
    picasso.cancelRequest(target);
    unPauseMainLooper();
    verifyZeroInteractions(target);
    assertThat(picasso.targetsToRequests).isEmpty();
  }

  @Test public void loaderRuntimeExceptionsBubbleUp() throws Exception {
    Picasso picasso = create(NPE_ANSWER, NULL_ANSWER);
    ImageView target = mock(ImageView.class);
    picasso.load(URI_1).into(target);
    try {
      executor.flush();
      fail("Loader should have thrown exception back to the main thread.");
    } catch (ExecutionException e) {
      // Find the lowest underlying cause of the exception.
      Throwable cause = e;
      while (cause.getCause() != null && cause.getCause() != cause) {
        cause = cause.getCause();
      }

      assertThat(cause).isInstanceOf(NullPointerException.class);
    }
  }

  @Test public void invokesCacheMissAndCacheHitProperly() throws Exception {
    Picasso picasso = create(LOADER_ANSWER, BITMAP1_ANSWER);
    ImageView target = mock(ImageView.class);
    picasso.load(URI_1).into(target);
    executor.flush();

    verify(stats).cacheMiss();

    when(cache.get(anyString())).thenReturn(bitmap1);

    picasso.load(URI_1).into(target);
    executor.flush();

    verify(stats).cacheHit();
  }

  @Test public void listenerCalledAfterRetries() throws Exception {
    Picasso picasso = create(NULL_ANSWER, IO_EXCEPTION_ANSWER);

    ImageView target = mock(ImageView.class);
    Request request =
        new Request(picasso, URI_1, 0, target, null, null, Request.Type.NETWORK, false, false, 0,
            null);

    retryRequest(picasso, request);

    verify(listener).onImageLoadFailed(picasso, URI_1);
  }

  private void retryRequest(Picasso picasso, Request request) throws Exception {
    picasso.submit(request);

    for (int i = Request.DEFAULT_RETRY_COUNT; i >= 0; i--) {
      executor.flush();
      runUiThreadTasksIncludingDelayedTasks();
    }
  }

  private Picasso create(Answer loaderAnswer, Answer decoderAnswer) throws IOException {
    Picasso picasso = new Picasso(context, loader, executor, cache, listener, stats);
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
