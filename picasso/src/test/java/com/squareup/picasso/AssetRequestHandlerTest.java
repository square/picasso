package com.squareup.picasso;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricGradleTestRunner;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricGradleTestRunner.class)
public class AssetRequestHandlerTest {
  @Mock Context context;

  @Before public void setUp() {
    initMocks(this);
  }

  @Test public void truncatesFilePrefix() throws IOException {
    Uri uri = Uri.parse("file:///android_asset/foo/bar.png");
    Request request = new Request.Builder(uri).build();

    String actual = AssetRequestHandler.getFilePath(request);
    assertThat(actual).isEqualTo("foo/bar.png");
//
//    main(null);

  }
  @Test public void truncatesFilePrefix1() throws IOException {
//    Uri uri = Uri.parse("file:///android_asset/foo/bar.png");
//    Request request = new Request.Builder(uri).build();
//
//    String actual = AssetRequestHandler.getFilePath(request);
//    assertThat(actual).isEqualTo("foo/bar.png");

    main(null);

  }



  public static void main(String[] args) {
    //创建线程池
    ExecutorService es = Executors.newSingleThreadExecutor();
    //创建Callable对象任务
    CallableDemo1 calTask = new CallableDemo1();
    //提交任务并获取执行结果
    Future<?> future = es.submit(calTask);
    //关闭线程池
    es.shutdown();
    try {
      Thread.sleep(2000);
      System.out.println("主线程在执行其他任务");

      System.out.println(" future.cancel(true)--->" +  future.cancel(false));


//方法用来取消任务，如果取消任务成功则返回true，
// 如果取消任务失败则返回false。

// 参数mayInterruptIfRunning表示是否允许取消正在执行却没有执行完毕的任务，
// 如果设置true，则表示可以取消正在执行过程中的任务。 //已经完成的相当于消息失败


// 如果任务已经完成，则无论mayInterruptIfRunning为true还是false，
// 此方法肯定返回false，即如果取消已经完成的任务会返回false；


// 如果任务正在执行，若mayInterruptIfRunning设置为true，则返回true，
// 若mayInterruptIfRunning设置为false，则返回false； //相当于取消失败，而传入false则会让线程正常执行至完成，并返回false。
//
// 如果任务还没有执行，则无论mayInterruptIfRunning为true还是false，肯定返回true。




      if (future.get() != null) {
        //输出获取到的结果
        System.out.println("future.get()-->" + future.get());
      } else {
        //输出获取到的结果
        System.out.println("future.get()未获取到结果");
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("主线程在执行完成");

  }
  public static  class CallableDemo implements Callable<Integer> {

    private int sum;

    @Override
    public Integer call() throws Exception {
      System.out.println("Callable子线程开始计算啦！");
      Thread.sleep(2000);

      for (int i = 0; i < 5000; i++) {
        sum = sum + i;
      }
      System.out.println("Callable子线程计算结束！");
      return sum;
    }
  }
  public static  class CallableDemo1 implements Runnable {

    private int sum;

//    @Override
//    public Integer call() throws Exception {
//      System.out.println("Callable子线程开始计算啦！");
//      Thread.sleep(2000);
//
//      for (int i = 0; i < 5000; i++) {
//        sum = sum + i;
//      }
//      System.out.println("Callable子线程计算结束！");
//      return sum;
//    }

    @Override
    public void run() {
      System.out.println("Callable子线程开始计算啦！");
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      for (int i = 0; i < 5000; i++) {
        sum = sum + i;
      }
      System.out.println("Callable子线程计算结束！");
      return;
    }
  }

}
