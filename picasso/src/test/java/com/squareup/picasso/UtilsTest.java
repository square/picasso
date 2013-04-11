package com.squareup.picasso;

import com.squareup.picasso.transformations.ResizeTransformation;
import com.squareup.picasso.transformations.RotationTransformation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.squareup.picasso.Utils.createKey;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(PicassoTestRunner.class)
public class UtilsTest {
  private static final String URL = "http://example.com/a.png";
  private static final List<Transformation> NONE = Collections.emptyList();

  private Picasso picasso = mock(Picasso.class);
  private RequestMetrics metrics = mock(RequestMetrics.class);

  @Test public void matchingRequestsHaveSameKey() {
    Request r1 = new Request(picasso, URL, 0, null, null, NONE, metrics, null, 0, null);
    Request r2 = new Request(picasso, URL, 0, null, null, NONE, metrics, null, 0, null);
    assertThat(createKey(r1)).isEqualTo(createKey(r2));

    List<Transformation> t1 = new ArrayList<Transformation>();
    t1.add(new ResizeTransformation(20, 20));

    List<Transformation> t2 = new ArrayList<Transformation>();
    t2.add(new ResizeTransformation(20, 20));
    Request single1 = new Request(picasso, URL, 0, null, null, t1, metrics, null, 0, null);
    Request single2 = new Request(picasso, URL, 0, null, null, t2, metrics, null, 0, null);
    assertThat(createKey(single1)).isEqualTo(createKey(single2));

    List<Transformation> t3 = new ArrayList<Transformation>();
    t3.add(new ResizeTransformation(20, 20));
    t3.add(new RotationTransformation(-50));

    List<Transformation> t4 = new ArrayList<Transformation>();
    t4.add(new ResizeTransformation(20, 20));
    t4.add(new RotationTransformation(-50));
    Request double1 = new Request(picasso, URL, 0, null, null, t3, metrics, null, 0, null);
    Request double2 = new Request(picasso, URL, 0, null, null, t4, metrics, null, 0, null);
    assertThat(createKey(double1)).isEqualTo(createKey(double2));

    List<Transformation> t5 = new ArrayList<Transformation>();
    t5.add(new ResizeTransformation(20, 20));
    t5.add(new RotationTransformation(-50));

    List<Transformation> t6 = new ArrayList<Transformation>();
    t6.add(new RotationTransformation(-50));
    t6.add(new ResizeTransformation(20, 20));
    Request order1 = new Request(picasso, URL, 0, null, null, t5, metrics, null, 0, null);
    Request order2 = new Request(picasso, URL, 0, null, null, t6, metrics, null, 0, null);
    assertThat(createKey(order1)).isNotEqualTo(createKey(order2));
  }
}
