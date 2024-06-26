package com._4paradigm.openmldb.featureplatform.service;

import com._4paradigm.openmldb.common.Pair;
import com._4paradigm.openmldb.featureplatform.dao.model.DatabaseTable;
import com._4paradigm.openmldb.featureplatform.dao.model.FeatureService;
import com._4paradigm.openmldb.featureplatform.dao.model.LatestFeatureService;
import com._4paradigm.openmldb.featureplatform.dao.model.ThreadLocalSqlExecutor;
import com._4paradigm.openmldb.featureplatform.utils.*;
import com._4paradigm.openmldb.jdbc.SQLResultSet;
import com._4paradigm.openmldb.proto.Common;
import com._4paradigm.openmldb.proto.NS;
import com._4paradigm.openmldb.sdk.Column;
import com._4paradigm.openmldb.sdk.Schema;
import com._4paradigm.openmldb.sdk.impl.SqlClusterExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Repository
public class FeatureServiceService {

    @Autowired
    private Environment env;

    public static FeatureService resultSetToFeatureService(ResultSet resultSet) throws SQLException {
        return new FeatureService(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3),
                resultSet.getString(4), resultSet.getString(5), resultSet.getString(6),
                resultSet.getString(7));
    }

    public List<FeatureService> getFeatureServices() throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        ArrayList<FeatureService> featureServices = new ArrayList<>();

        String sql = "SELECT name, version, feature_names, description, db, sql, deployment FROM SYSTEM_FEATURE_PLATFORM.feature_services";
        statement.execute(sql);
        ResultSet resultSet = statement.getResultSet();

        while (resultSet.next()) {
            FeatureService featureService = resultSetToFeatureService(resultSet);
            featureServices.add(featureService);
        }

        statement.close();
        return featureServices;
    }

    public Map<String, String> getLatestServiceNameVersionMap() throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        Map<String, String> latestNameVersionMap = new HashMap<>();

        String sql = "SELECT name, version FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services";
        statement.execute(sql);
        ResultSet result = statement.getResultSet();

        while (result.next()) {
            String name = result.getString(1);
            String version = result.getString(2);
            latestNameVersionMap.put(name, version);
        }

        statement.close();
        return latestNameVersionMap;
    }

    public List<FeatureService> getLatestFeatureServices() throws SQLException {
        ArrayList<FeatureService> outputFeatureServices = new ArrayList<>();

        Map<String, String> latestNameVersionMap = getLatestServiceNameVersionMap();
        List<FeatureService> allFeatureServices = getFeatureServices();

        // Filter and only get the latest version
        for (FeatureService featureService: allFeatureServices) {
            if (latestNameVersionMap.containsKey(featureService.getName())) {
                if (latestNameVersionMap.get(featureService.getName()).equals(featureService.getVersion())) {
                    outputFeatureServices.add(featureService);
                }
            }
        }

        return outputFeatureServices;
    }

    public FeatureService getFeatureServiceByName(String name) throws SQLException {
        String latestVersion = getLatestVersion(name);
        return getFeatureServiceByNameAndVersion(name, latestVersion);
    }

    public static LatestFeatureService resultSetToLatestFeatureService(ResultSet resultSet) throws SQLException {
        return new LatestFeatureService(resultSet.getString(1), resultSet.getString(2),
                resultSet.getString(3), resultSet.getString(4));
    }

    public LatestFeatureService getLatestFeatureServiceByName(String name) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        String sql = String.format("SELECT name, version, db, deployment FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services WHERE name='%s'", name);
        statement.execute(sql);
        ResultSet resultSet = statement.getResultSet();

        ResultSetUtil.assertSizeIsOne(resultSet);
        resultSet.next();

        LatestFeatureService latestFeatureService = resultSetToLatestFeatureService(resultSet);

        statement.close();
        return latestFeatureService;
    }

    public FeatureService getFeatureServiceByNameAndVersion(String name, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        String sql = String.format("SELECT name, version, feature_names, description, db, sql, deployment FROM SYSTEM_FEATURE_PLATFORM.feature_services WHERE name='%s' AND version='%s'", name, version);
        statement.execute(sql);
        ResultSet resultSet = statement.getResultSet();

        ResultSetUtil.assertSizeIsOne(resultSet);
        resultSet.next();
        FeatureService featureService = resultSetToFeatureService(resultSet);

        statement.close();
        return featureService;
    }

    public boolean checkIfFeatureServiceExists(String name, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        String sql = String.format("SELECT name, version, feature_names, description, db, sql, deployment FROM SYSTEM_FEATURE_PLATFORM.feature_services WHERE name='%s' AND version='%s'", name, version);
        statement.execute(sql);
        ResultSet resultSet = statement.getResultSet();
        int resultSize = resultSet.getFetchSize();
        statement.close();

        if (resultSize == 0) {
            return false;
        } else {
            return true;
        }
    }

    public void assertServiceNotExist(FeatureService newFeatureService) throws SQLException {
        List<FeatureService> allFeatureServices = getFeatureServices();
        for (FeatureService featureService: allFeatureServices) {
            if (featureService.getName().equals(newFeatureService.getName()) &&
                    featureService.getVersion().equals(newFeatureService.getVersion())) {
                throw new SQLException(String.format("The feature service has exists, name: %s and version: %s",
                        featureService.getName(), featureService.getVersion()));
            }
        }
    }

    public FeatureService createFeatureService(FeatureService featureService) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        assertServiceNotExist(featureService);

        if(checkIfFeatureServiceExists(featureService.getName(), featureService.getVersion())) {
            throw new SQLException("The feature service exists, abort creating");
        }

        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        FeatureViewService featureViewService = new FeatureViewService();

        String featureSetString = featureService.getFeatureNames();

        String deploymentName = String.format("FEATURE_PLATFORM_%s_%s", featureService.getName(), featureService.getVersion());

        List<String> joinKeys = new ArrayList<>(Arrays.asList(featureService.getMainTableKeys().split(",")));
        String mergedSql = FeatureSetUtil.featureSetToSql(sqlExecutor, featureViewService, featureSetString, joinKeys);
        String db = FeatureSetUtil.getDbFromFeatureSet(featureViewService, featureSetString);

        boolean isSkipIndexCheck = env.getProperty("openmldb.skip_index_check", Boolean.class, false);
        String deploySql = String.format("DEPLOY %s %s", deploymentName, mergedSql);
        if (isSkipIndexCheck) {
            deploySql = String.format("DEPLOY %s OPTIONS (SKIP_INDEX_CHECK=\"TRUE\") %s", deploymentName, mergedSql);
        }

        statement.execute(String.format("USE %s", db));
        statement.execute(deploySql);

        String newFeatureNames = String.join(",", FeatureSetUtil.featureSetToFeatureNames(featureViewService, featureSetString));

        FeatureService newFeatureService = new FeatureService(featureService.getName(), featureService.getVersion(),
            newFeatureNames, featureService.getDescription(), db, deploySql, deploymentName);

        // Insert into database
        String sql = String.format("INSERT INTO SYSTEM_FEATURE_PLATFORM.feature_services (name, version, feature_names, " +
                "description, db, sql, deployment) values ('%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                newFeatureService.getName(), newFeatureService.getVersion(), newFeatureService.getFeatureNames(),
                newFeatureService.getDescription(), newFeatureService.getDb(), newFeatureService.getSql(),
                newFeatureService.getDeployment());
        statement.execute(sql);

        // Add to latest feature service
        sql = String.format("SELECT count(name) FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services WHERE name='%s'",
                newFeatureService.getName());
        statement.execute(sql);
        ResultSet resultSet = statement.getResultSet();
        ResultSetUtil.assertSizeIsOne(resultSet);
        resultSet.next();

        if (resultSet.getLong(1) == 0) { // Has no other versions
            sql = String.format("INSERT INTO SYSTEM_FEATURE_PLATFORM.latest_feature_services " +
                    "(name, version, db, deployment) values ('%s', '%s', '%s', '%s')", newFeatureService.getName(),
                    newFeatureService.getVersion(), newFeatureService.getDb(), newFeatureService.getDeployment());
            statement.execute(sql);
        }

        statement.close();
        return newFeatureService;
    }

    public List<String> getFeatureServiceVersions(String name) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        ArrayList<String> versions = new ArrayList<>();

        String sql = String.format("SELECT version FROM SYSTEM_FEATURE_PLATFORM.feature_services WHERE name='%s'", name);
        statement.execute(sql);
        ResultSet result = statement.getResultSet();
        while (result.next()) {
            String version = result.getString(1);
            versions.add(version);
        }

        statement.close();
        return versions;
    }

    public void deleteFeatureService(String name) throws SQLException {
        List<String> versions = getFeatureServiceVersions(name);

        for (String version : versions) {
            deleteFeatureService(name, version);
        }
    }

    public void deleteFeatureService(String name, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        FeatureService featureService = getFeatureServiceByNameAndVersion(name, version);

        // Delete the deployment
        statement.execute("USE " + featureService.getDb());
        String dropDeploymentSql = String.format("DROP DEPLOYMENT %s", featureService.getDeployment());
        statement.execute(dropDeploymentSql);

        // Delete record in database
        String sql = String.format("DELETE FROM SYSTEM_FEATURE_PLATFORM.feature_services " +
                "WHERE name='%s' AND version='%s'", name, version);
        statement.execute(sql);

        // Delete if it is the latest version
        if (getLatestVersion(name).equals(version)) {
            sql = String.format("DELETE FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services " +
                    "WHERE name='%s' AND version='%s'", name, version);
            statement.execute(sql);
        }

        statement.close();
    }

    public void updateLatestVersion(String name, String newVersion) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        String oldLatestVersion = getLatestVersion(name);

        if (!oldLatestVersion.equals(newVersion)) {
            Statement statement = sqlExecutor.getStatement();
            statement.execute("SET @@execute_mode='online'");

            String sql = String.format("DELETE FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services " +
                    "WHERE name='%s' AND version='%s'", name, oldLatestVersion);
            statement.execute(sql);

            FeatureService featureService = getFeatureServiceByNameAndVersion(name, newVersion);
            sql = String.format("INSERT INTO SYSTEM_FEATURE_PLATFORM.latest_feature_services " +
                    "(name, version, db, deployment) values ('%s', '%s', '%s', '%s')",
                    featureService.getName(), featureService.getVersion(), featureService.getDb(),
                    featureService.getDeployment());
            statement.execute(sql);

            statement.close();
        }
    }

    public ResponseEntity<String> requestFeatureService(String name, String requestData) throws IOException, SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        String apiServerEndpoint = env.getProperty("openmldb.apiserver");
        if (apiServerEndpoint == null || apiServerEndpoint.equals("")) {
            throw new IOException("Need to config openmldb.apiserver in application.yml");
        }

        // TODO: Get the db from feature service
        FeatureServiceService featureServiceService = new FeatureServiceService();
        LatestFeatureService featureService = featureServiceService.getLatestFeatureServiceByName(name);
        String db = featureService.getDb();
        String deployment = featureService.getDeployment();

        HttpClient httpClient = HttpClients.createDefault();
        String endpoint = String.format("http://%s/dbs/%s/deployments/%s", apiServerEndpoint, db, deployment);
        HttpPost postRequest = new HttpPost(endpoint);
        postRequest.setHeader("Content-Type", "application/json");
        postRequest.setEntity(new StringEntity(requestData));
        HttpResponse response = httpClient.execute(postRequest);

        int statusCode = response.getStatusLine().getStatusCode();
        HttpEntity responseEntity = response.getEntity();
        String responseBody = EntityUtils.toString(responseEntity);
        return new ResponseEntity<String>(responseBody, HttpStatus.valueOf(statusCode));
    }

    public ResponseEntity<String> requestFeatureService(String name, String version, String requestData) throws IOException, SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        String apiServerEndpoint = env.getProperty("openmldb.apiserver");
        if (apiServerEndpoint == null || apiServerEndpoint.equals("")) {
            throw new IOException("Need to config openmldb.apiserver in application.yml");
        }

        // TODO: Get the db from feature service
        FeatureServiceService featureServiceService = new FeatureServiceService();
        FeatureService featureService = featureServiceService.getFeatureServiceByNameAndVersion(name, version);
        String db = featureService.getDb();
        String deployment = featureService.getDeployment();

        HttpClient httpClient = HttpClients.createDefault();
        String endpoint = String.format("http://%s/dbs/%s/deployments/%s", apiServerEndpoint, db, deployment);
        HttpPost postRequest = new HttpPost(endpoint);
        postRequest.setHeader("Content-Type", "application/json");
        postRequest.setEntity(new StringEntity(requestData));
        HttpResponse response = httpClient.execute(postRequest);

        int statusCode = response.getStatusLine().getStatusCode();
        HttpEntity responseEntity = response.getEntity();
        String responseBody = EntityUtils.toString(responseEntity);
        return new ResponseEntity<String>(responseBody, HttpStatus.valueOf(statusCode));
    }

    public List<List<String>> requestOnlineQueryMode(String name, String version, String requestData) throws IOException, SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        // TODO: Get the db from feature service
        FeatureServiceService featureServiceService = new FeatureServiceService();
        FeatureService featureService = featureServiceService.getFeatureServiceByNameAndVersion(name, version);
        String db = featureService.getDb();
        String deploymentSql = featureService.getSql();

        String querySql = OpenmldbSqlUtil.removeDeployFromSql(deploymentSql);

        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(requestData, JsonObject.class);
        JsonArray dataJsonArray = jsonObject.getAsJsonArray("data");
        JsonArray indexJsonArray = jsonObject.getAsJsonArray("index");

        // TODO: Only Support one row, need to support 'select * from t1 where (name, gender) in (("tobe", "male"))'

        List<String> indexNames = new ArrayList<>();
        for (JsonElement element: indexJsonArray) {
            indexNames.add(element.getAsString());
        }

        List<String> indexDataValues = new ArrayList<>();

        for (JsonElement dataElement : dataJsonArray) {
            JsonArray innerArray = dataElement.getAsJsonArray();

            for (JsonElement innerElement : innerArray) {
                indexDataValues.add(innerElement.getAsString());
            }
        }

        String finalSql = querySql;
        for (int i=0; i<indexNames.size(); ++i) {

            if (i == 0) {
                // TODO: Handle single quote and double quote
                finalSql += String.format(" WHERE %s = '%s'", indexNames.get(i), indexDataValues.get(i));
            } else {
                finalSql += String.format(" AND %s = '%s'", indexNames.get(i), indexDataValues.get(i));
            }

        }

        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");
        statement.execute(String.format("USE %s", db));

        statement.execute(finalSql);

        List<List<String>> returnList = new ArrayList<>();

        // TODO: Check if has result set
        SQLResultSet resultSet = (SQLResultSet) statement.getResultSet();
        returnList = ResultSetUtil.resultSetToStringArray(resultSet);
        resultSet.close();

        return returnList;
    }

    public List<List<String>> requestOnlineQueryModeSamples(String name, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        FeatureServiceService featureServiceService = new FeatureServiceService();
        FeatureService featureService = featureServiceService.getFeatureServiceByNameAndVersion(name, version);
        String db = featureService.getDb();
        String deploymentSql = featureService.getSql();

        String querySql = OpenmldbSqlUtil.removeDeployFromSql(deploymentSql);

        String finalSql = String.format("%s limit 10", querySql);

        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");
        statement.execute(String.format("USE %s", db));

        statement.execute(finalSql);

        List<List<String>> returnList = new ArrayList<>();

        // TODO: Check if has result set
        SQLResultSet resultSet = (SQLResultSet) statement.getResultSet();
        returnList = ResultSetUtil.resultSetToStringArray(resultSet);
        resultSet.close();

        return returnList;
    }
    public List<String> getIndexNames(String name, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();

        FeatureServiceService featureServiceService = new FeatureServiceService();
        FeatureService featureService = featureServiceService.getFeatureServiceByNameAndVersion(name, version);
        String db = featureService.getDb();
        String deploymentSql = featureService.getSql();

        String querySql = OpenmldbSqlUtil.removeDeployFromSql(deploymentSql);

        String mainTableName = OpenmldbTableUtil.getMainTableName(sqlExecutor, querySql, db);

        NS.TableInfo tableInfo = sqlExecutor.getTableInfo(db, mainTableName);

        // For example: ["name", "name,age"]
        List<String> indexColumnNames = new ArrayList<>();


        for (Common.ColumnKey columnKey: tableInfo.getColumnKeyList()) {
            if(columnKey.getFlag() == 0){
                List<String> columnNameList = new ArrayList<>();
                for (String columnName: columnKey.getColNameList()) {
                    columnNameList.add(columnName);
                }

                indexColumnNames.add(String.join(",", columnNameList));
            }
        }

        return indexColumnNames;
    }



    public Schema getRequestSchema(String serviceName, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        FeatureService featureService = getFeatureServiceByNameAndVersion(serviceName, version);
        String sql = featureService.getSql();
        String db = featureService.getDb();

        String selectSql = OpenmldbSqlUtil.removeDeployFromSql(sql);


        Schema schema = OpenmldbTableUtil.getMainTableSchema(sqlExecutor, selectSql, db);
        return schema;
    }

    public Schema getRequestSchema(String serviceName) throws SQLException {
        String latestVersion = getLatestVersion(serviceName);
        return getRequestSchema(serviceName, latestVersion);
    }

    public String getRequestDemoData(String serviceName, String version) throws SQLException {
        Schema schema = getRequestSchema(serviceName, version);

        // "{\"input\": [[\"abc\", 22]]}"
        StringBuilder demoBuilder = new StringBuilder();
        demoBuilder.append("{\"input\": [[");

        for (int i = 0; i < schema.getColumnList().size(); ++i) {
            Column column = schema.getColumnList().get(i);
            column.getSqlType();
            String demoValue = TypeUtil.javaSqlTypeToDemoData(column.getSqlType());

            if (i != 0) {
                demoBuilder.append(", ");
            }

            demoBuilder.append(demoValue);
        }

        demoBuilder.append("]]}");
        return demoBuilder.toString();
    }

    public String getRequestDemoData(String serviceName) throws SQLException {
        String latestVersion = getLatestVersion(serviceName);
        return getRequestDemoData(serviceName, latestVersion);
    }

    public String getLatestVersion(String serviceName) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        String sql = String.format("SELECT version FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services WHERE name='%s'"
                , serviceName);
        statement.execute(sql);
        ResultSet resultSet = statement.getResultSet();

        ResultSetUtil.assertSizeIsOne(resultSet);
        resultSet.next();

        String version = resultSet.getString(1);

        statement.close();
        return version;
    }

    public List<String> getDependentTables(String name, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        FeatureService featureService = getFeatureServiceByNameAndVersion(name, version);

        String selectSql = OpenmldbSqlUtil.removeDeployFromSql(featureService.getSql());

        List<Pair<String, String>> tables = SqlClusterExecutor.getDependentTables(selectSql,
                featureService.getDb(), OpenmldbTableUtil.getSystemSchemaMaps(sqlExecutor));

        List<String> fullNameTables = new ArrayList<>();
        for (Pair<String, String> tableItem : tables) {
            fullNameTables.add(tableItem.getKey() + "." + tableItem.getValue());
        }
        return fullNameTables;
    }

    public List<String> getDependentTables(String name) throws SQLException {
        String latestVersion = getLatestVersion(name);
        return getDependentTables(name, latestVersion);
    }

    public Schema getOutputSchema(String serviceName, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        FeatureService featureService = getFeatureServiceByNameAndVersion(serviceName, version);
        String sql = featureService.getSql();
        String db = featureService.getDb();

        String selectSql = OpenmldbSqlUtil.removeDeployFromSql(sql);

        Schema schema = SqlClusterExecutor.genOutputSchema(selectSql, db, OpenmldbTableUtil.getSystemSchemaMaps(sqlExecutor));
        return schema;
    }

    public Schema getOutputSchema(String serviceName) throws SQLException {
        String latestVersion = getLatestVersion(serviceName);
        return getOutputSchema(serviceName, latestVersion);
    }

    public DatabaseTable getMainTable(String name, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        FeatureService featureService = getFeatureServiceByNameAndVersion(name, version);
        String sql = featureService.getSql();
        String db = featureService.getDb();

        String selectSql = OpenmldbSqlUtil.removeDeployFromSql(sql);

        return OpenmldbTableUtil.getMainTable(sqlExecutor, selectSql, db);
    }
}