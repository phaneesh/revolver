/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.dropwizard.revolver.degrade;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DegradeRegistry {

  private static DegradeRegistry instance;

  private ConcurrentHashMap<String, Double> threadPoolDegradeMap;

  private ConcurrentHashMap<String, Double> timeoutDegradeMap;

  private DegradeRegistry() {
    threadPoolDegradeMap = new ConcurrentHashMap<>();
    timeoutDegradeMap = new ConcurrentHashMap<>();
  }

  public static synchronized DegradeRegistry getInstance() {
    if(instance == null) {
      instance = new DegradeRegistry();
    }
    return instance;
  }

  public void addThreadPoolDegrade(final String key, final double factor) {
    threadPoolDegradeMap.put(key, factor);
  }

  public void removeThreadPoolDegrade(final String key) {
    threadPoolDegradeMap.remove(key);
  }

  public void addTimeoutDegrade(final String key, final double factor) {
    timeoutDegradeMap.put(key, factor);
  }

  public void removeTimeoutDegrade(final String key) {
    timeoutDegradeMap.remove(key);
  }

  public int getDegradedThreadPool(final String key, int originalThreadpoolSize) {
    if(!threadPoolDegradeMap.containsKey(key)) {
      return originalThreadpoolSize;
    }
    return (int)Math.round(originalThreadpoolSize * threadPoolDegradeMap.get(key));
  }

  public int getDegradedTimeout(final String key, int originalTimeout) {
    if(!timeoutDegradeMap.containsKey(key)) {
      return originalTimeout;
    }
    return (int)Math.round(originalTimeout * threadPoolDegradeMap.get(key));
  }

  public ConcurrentMap<String, Double> getThreadPoolDegradeStatus() {
    return threadPoolDegradeMap;
  }

  public ConcurrentMap<String, Double> getTimeoutDegradeStatus() {
    return timeoutDegradeMap;
  }


  public boolean isThreadPoolDegraded(final String key) {
    return threadPoolDegradeMap.containsKey(key);
  }

  public boolean isTimeoutDegraded(final String key) {
    return timeoutDegradeMap.containsKey(key);
  }

}
