/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.judge

import com.netflix.kayenta.judge.preprocessing.Transforms.{removeNaNs, removeOutliers, replaceNaNs}
import com.netflix.kayenta.judge.detectors.{IQRDetector, KSigmaDetector}
import com.netflix.kayenta.judge.preprocessing.Transforms
import com.netflix.kayenta.judge.utils.RandomUtils
import org.apache.commons.math3.stat.StatUtils
import org.scalatest.FunSuite
import org.scalatest.Matchers._


class TransformSuite extends FunSuite {

  test("Remove Single NaN"){
    val testData = Array(0.0, 1.0, Double.NaN, 1.0, 0.0)
    val truth = Array(0.0, 1.0, 1.0, 0.0)

    val result = Transforms.removeNaNs(testData)
    assert(result === truth)
  }

  test("Remove Multiple NaN") {
    val testData = Array(Double.NaN, Double.NaN, Double.NaN)
    val truth = Array[Double]()

    val result = removeNaNs(testData)
    assert(result === truth)
  }

  test("Replace NANs") {
    val testData = Array(Double.NaN, 10.10, 20.20, 30.30, Double.NaN, 40.40)
    val truth = Array(0.0, 10.10, 20.20, 30.30, 0.0, 40.40)

    val result = replaceNaNs(testData, 0.0)
    assert(result === truth)
  }

  test("IQR Outlier Removal") {
    val testData = Array(21.0, 23.0, 24.0, 25.0, 50.0, 29.0, 23.0, 21.0)
    val truth = Array(21.0, 23.0, 24.0, 25.0, 29.0, 23.0, 21.0)

    val detector = new IQRDetector(factor = 1.5)
    val result = removeOutliers(testData, detector)
    assert(result === truth)
  }

  test("KSigma Outlier Removal") {
    val testData = Array(1.0, 1.0, 1.0, 1.0, 1.0, 20.0, 1.0, 1.0, 1.0, 1.0, 1.0)
    val truth = Array(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)

    val detector = new KSigmaDetector(k = 3.0)
    val result = removeOutliers(testData, detector)
    assert(result === truth)
  }

  test("Add Gaussian Noise"){
    val seed = 12345
    RandomUtils.init(seed)

    val testData = Array(0.0, 0.0, 0.0, 0.0, 0.0)
    val transformed = Transforms.addGaussianNoise(testData, mean = 1.0, stdev = 1.0)

    val transformedMean = StatUtils.mean(transformed)
    val transformedStdev = math.sqrt(StatUtils.variance(transformed))

    assert(transformedMean === (1.0 +- 0.2))
    assert(transformedStdev === (1.0 +- 0.2))
    assert(transformed.length === testData.length)
  }



}
