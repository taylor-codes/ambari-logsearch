/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ambari.logsearch.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.apache.ambari.logsearch.common.MessageEnums;
import org.apache.ambari.logsearch.model.response.LogData;
import org.apache.ambari.logsearch.model.response.LogSearchResponse;
import org.apache.ambari.logsearch.dao.SolrDaoBase;
import org.apache.ambari.logsearch.util.JSONUtil;
import org.apache.ambari.logsearch.util.RESTErrorUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.data.solr.core.DefaultQueryParser;
import org.springframework.data.solr.core.query.SimpleQuery;
import org.springframework.data.solr.core.query.SolrDataQuery;

public abstract class ManagerBase<LOG_DATA_TYPE extends LogData, SEARCH_RESPONSE extends LogSearchResponse> extends JsonManagerBase {
  private static final Logger logger = Logger.getLogger(ManagerBase.class);

  public ManagerBase() {
    super();
  }

  public String getHadoopServiceConfigJSON() {
    StringBuilder result = new StringBuilder("");

    // Get file from resources folder
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("HadoopServiceConfig.json").getFile());

    try (Scanner scanner = new Scanner(file)) {

      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        result.append(line).append("\n");
      }

      scanner.close();

    } catch (IOException e) {
      logger.error("Unable to read HadoopServiceConfig.json", e);
      throw RESTErrorUtil.createRESTException(e.getMessage(), MessageEnums.ERROR_SYSTEM);
    }

    String hadoopServiceConfig = result.toString();
    if (JSONUtil.isJSONValid(hadoopServiceConfig)) {
      return hadoopServiceConfig;
    }
    throw RESTErrorUtil.createRESTException("Improper JSON", MessageEnums.ERROR_SYSTEM);

  }
  
  protected SEARCH_RESPONSE getLastPage(SolrDaoBase solrDoaBase, SimpleQuery lastPageQuery, String event) {
    int maxRows = lastPageQuery.getRows();
    SEARCH_RESPONSE logResponse = getLogAsPaginationProvided(lastPageQuery, solrDoaBase, event);
    Long totalLogs = solrDoaBase.count(lastPageQuery);
    int startIndex = Integer.parseInt("" + ((totalLogs / maxRows) * maxRows));
    int numberOfLogsOnLastPage = Integer.parseInt("" + (totalLogs - startIndex));
    logResponse.setStartIndex(startIndex);
    logResponse.setTotalCount(totalLogs);
    logResponse.setPageSize(maxRows);
    List<LOG_DATA_TYPE> docList = logResponse.getLogList();
    List<LOG_DATA_TYPE> lastPageDocList = new ArrayList<>();
    logResponse.setLogList(lastPageDocList);
    int cnt = 0;
    for (LOG_DATA_TYPE doc : docList) {
      if (cnt < numberOfLogsOnLastPage) {
        lastPageDocList.add(doc);
      }
      cnt++;
    }
    Collections.reverse(lastPageDocList);
    return logResponse;
  }

  protected SEARCH_RESPONSE getLogAsPaginationProvided(SolrDataQuery solrQuery, SolrDaoBase solrDaoBase, String event) {
    SolrQuery query = new DefaultQueryParser().doConstructSolrQuery(solrQuery);
    return getLogAsPaginationProvided(query, solrDaoBase, event);
  }


  protected SEARCH_RESPONSE getLogAsPaginationProvided(SolrQuery solrQuery, SolrDaoBase solrDaoBase, String event) {
    QueryResponse response = solrDaoBase.process(solrQuery, event);
    SEARCH_RESPONSE logResponse = createLogSearchResponse();
    SolrDocumentList docList = response.getResults();
    List<LOG_DATA_TYPE> serviceLogDataList = convertToSolrBeans(response);
    if (CollectionUtils.isNotEmpty(docList)) {
      logResponse.setLogList(serviceLogDataList);
      logResponse.setStartIndex((int) docList.getStart());
      logResponse.setTotalCount(docList.getNumFound());
      Integer rowNumber = solrQuery.getRows();
      if (rowNumber == null) {
        logger.error("No RowNumber was set in solrQuery");
        return createLogSearchResponse();
      }
      logResponse.setPageSize(rowNumber);
    }
    return logResponse;
  }

  protected abstract List<LOG_DATA_TYPE> convertToSolrBeans(QueryResponse response);

  protected abstract SEARCH_RESPONSE createLogSearchResponse();
}
