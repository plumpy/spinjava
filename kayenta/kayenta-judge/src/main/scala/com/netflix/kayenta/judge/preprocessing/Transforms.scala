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

package com.netflix.kayenta.judge.preprocessing

import com.netflix.kayenta.judge.Metric
import com.netflix.kayenta.judge.detectors.BaseOutlierDetector
import com.netflix.kayenta.judge.utils.RandomUtils


object Transforms {

  /**
    * Remove NaN values from the input array
    */
  def removeNaNs(data: Array[Double]): Array[Double] = {
    data.filter(x => !x.isNaN)
  }

  /**
    * Remove NaN values from the input metric
    */
  def removeNaNs(metric: Metric): Metric = {
    metric.copy(values = removeNaNs(metric.values))
  }

  /**
    * Replace NaN values with 0.0 from the input metric.
    */
  def replaceNaNs(metric: Metric): Metric = {
    metric.copy(values = replaceNaNs(metric.values, 0))
  }

  /**
    * Replace NaN values from the input array
    */
  def replaceNaNs(data: Array[Double], value: Double): Array[Double] = {
    data.map(x => if (x.isNaN) value else x)
  }

  /**
    * Remove outliers from the input array
    */
  def removeOutliers(data: Array[Double], detector: BaseOutlierDetector): Array[Double] = {
    val outliers = detector.detect(data)
    data.zip(outliers).collect{ case (v, false) => v }
  }

  /**
    * Remove outliers from the input metric
    */
  def removeOutliers(metric: Metric, detector: BaseOutlierDetector): Metric = {
    metric.copy(values = removeOutliers(metric.values, detector))
  }

  /**
    * Add Gaussian noise to the input array
    */
  def addGaussianNoise(data: Array[Double], mean: Double, stdev: Double): Array[Double] = {
    val noise = RandomUtils.normal(mean, stdev, data.length)
    (data, noise).zipped.map(_ + _)
  }

  /**
    * Add Gaussian noise to the input metric
    */
  def addGaussianNoise(metric: Metric, mean: Double, stdev: Double): Metric = {
    metric.copy(values = addGaussianNoise(metric.values, mean, stdev))
  }



}
