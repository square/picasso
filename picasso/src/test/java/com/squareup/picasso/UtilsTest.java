package com.squareup.picasso;

import com.squareup.picasso.transformations.ResizeTransformation;
import com.squareup.picasso.transformations.RotationTransformation;
import edu.emory.mathcs.backport.java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.squareup.picasso.Utils.createKey;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(PicassoTestRunner.class)
public class UtilsTest {
  private static final String URL = "http://example.com/a.png";
  private static final List<Transformation> NO_TRANSFORMATIONS = Collections.emptyList();

  private Picasso picasso = mock(Picasso.class);
  private RequestMetrics metrics = mock(RequestMetrics.class);

  @Test public void matchingRequestsHaveSameKey() {
    Request simple1 =
        new Request(picasso, URL, null, null, NO_TRANSFORMATIONS, metrics, 0, 0, null);
    Request simple2 =
        new Request(picasso, URL, null, null, NO_TRANSFORMATIONS, metrics, 0, 0, null);
    assertThat(createKey(simple1)).isEqualTo(createKey(simple2));

    List<Transformation> transformations1 = new ArrayList<Transformation>();
    transformations1.add(new ResizeTransformation(20, 20));

    List<Transformation> transformations2 = new ArrayList<Transformation>();
    transformations2.add(new ResizeTransformation(20, 20));
    Request single1 = new Request(picasso, URL, null, null, transformations1, metrics, 0, 0, null);
    Request single2 = new Request(picasso, URL, null, null, transformations2, metrics, 0, 0, null);
    assertThat(createKey(single1)).isEqualTo(createKey(single2));

    List<Transformation> transformations3 = new ArrayList<Transformation>();
    transformations3.add(new ResizeTransformation(20, 20));
    transformations3.add(new RotationTransformation(-50));

    List<Transformation> transformations4 = new ArrayList<Transformation>();
    transformations4.add(new ResizeTransformation(20, 20));
    transformations4.add(new RotationTransformation(-50));
    Request double1 = new Request(picasso, URL, null, null, transformations3, metrics, 0, 0, null);
    Request double2 = new Request(picasso, URL, null, null, transformations4, metrics, 0, 0, null);
    assertThat(createKey(double1)).isEqualTo(createKey(double2));

    List<Transformation> transformations5 = new ArrayList<Transformation>();
    transformations5.add(new ResizeTransformation(20, 20));
    transformations5.add(new RotationTransformation(-50));

    List<Transformation> transformations6 = new ArrayList<Transformation>();
    transformations6.add(new RotationTransformation(-50));
    transformations6.add(new ResizeTransformation(20, 20));
    Request order1 = new Request(picasso, URL, null, null, transformations5, metrics, 0, 0, null);
    Request order2 = new Request(picasso, URL, null, null, transformations6, metrics, 0, 0, null);
    assertThat(createKey(order1)).isNotEqualTo(createKey(order2));
  }
}
