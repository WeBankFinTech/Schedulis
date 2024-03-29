/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.utils;

/**
 * Like pair, but with 3 values.
 */
public class Triple<F, S, T> {

  private final F first;
  private final S second;
  private final T third;

  public Triple(final F first, final S second, final T third) {
    this.first = first;
    this.second = second;
    this.third = third;
  }

  public F getFirst() {
    return this.first;
  }

  public S getSecond() {
    return this.second;
  }

  public T getThird() {
    return this.third;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((this.first == null) ? 0 : this.first.hashCode());
    result = prime * result + ((this.second == null) ? 0 : this.second.hashCode());
    result = prime * result + ((this.third == null) ? 0 : this.third.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Triple other = (Triple) obj;
    if (this.first == null) {
      if (other.first != null) {
        return false;
      }
    } else if (!this.first.equals(other.first)) {
      return false;
    }
    if (this.second == null) {
      if (other.second != null) {
        return false;
      }
    } else if (!this.second.equals(other.second)) {
      return false;
    }
    if (this.third == null) {
      if (other.third != null) {
        return false;
      }
    } else if (!this.third.equals(other.third)) {
      return false;
    }
    return true;
  }

}
