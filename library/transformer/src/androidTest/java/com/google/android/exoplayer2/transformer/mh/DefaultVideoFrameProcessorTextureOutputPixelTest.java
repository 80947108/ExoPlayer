/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.transformer.mh;

import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.readBitmap;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.effect.BitmapOverlay;
import com.google.android.exoplayer2.effect.DefaultVideoFrameProcessor;
import com.google.android.exoplayer2.effect.OverlayEffect;
import com.google.android.exoplayer2.testutil.BitmapPixelTestUtil;
import com.google.android.exoplayer2.testutil.VideoFrameProcessorTestRunner;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for video frame processing, outputting to a texture, via {@link
 * DefaultVideoFrameProcessor}.
 *
 * <p>Uses a {@link DefaultVideoFrameProcessor} to process one frame, and checks that the actual
 * output matches expected output, either from a golden file or from another edit.
 */
// TODO(b/263395272): Move this test to effects/mh tests, and remove @TestOnly dependencies.
@RunWith(AndroidJUnit4.class)
public final class DefaultVideoFrameProcessorTextureOutputPixelTest {
  private static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";
  private static final String BITMAP_OVERLAY_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_bitmap_FrameProcessor.png";
  private static final String OVERLAY_PNG_ASSET_PATH = "media/bitmap/input_images/media3test.png";
  /** Input video of which we only use the first frame. */
  private static final String INPUT_SDR_MP4_ASSET_STRING = "media/mp4/sample.mp4";

  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;

  @After
  public void release() {
    checkNotNull(videoFrameProcessorTestRunner).release();
  }

  @Test
  public void noEffects_matchesGoldenFile() throws Exception {
    String testId = "noEffects_matchesGoldenFile";
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = videoFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void bitmapOverlay_matchesGoldenFile() throws Exception {
    String testId = "bitmapOverlay_matchesGoldenFile";
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new OverlayEffect(ImmutableList.of(bitmapOverlay)))
            .build();
    Bitmap expectedBitmap = readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH);

    Bitmap actualBitmap = videoFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  // TODO(b/227624622): Add a test for HDR input after BitmapPixelTestUtil can read HDR bitmaps,
  //  using GlEffectWrapper to ensure usage of intermediate textures.

  private VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    TextureBitmapReader textureBitmapReader = new TextureBitmapReader();
    DefaultVideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setOnTextureRenderedListener(textureBitmapReader::readBitmapFromTexture)
            .build();
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
        .setVideoAssetPath(INPUT_SDR_MP4_ASSET_STRING)
        .setBitmapReader(textureBitmapReader);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reads from an OpenGL texture. Only for use on physical devices.
   */
  private static final class TextureBitmapReader
      implements VideoFrameProcessorTestRunner.BitmapReader {
    // TODO(b/239172735): This outputs an incorrect black output image on emulators.

    private @MonotonicNonNull Bitmap outputBitmap;

    @Override
    public Surface getSurface(int width, int height) {
      int texId;
      try {
        texId = GlUtil.createExternalTexture();
      } catch (GlUtil.GlException e) {
        throw new RuntimeException(e);
      }
      SurfaceTexture surfaceTexture = new SurfaceTexture(texId);
      return new Surface(surfaceTexture);
    }

    @Override
    public Bitmap getBitmap() {
      return checkStateNotNull(outputBitmap);
    }

    public void readBitmapFromTexture(GlTextureInfo outputTexture, long presentationTimeUs)
        throws GlUtil.GlException {
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      outputBitmap =
          BitmapPixelTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
              outputTexture.width, outputTexture.height);
    }
  }
}
