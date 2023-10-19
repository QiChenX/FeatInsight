package com._4paradigm.openmldb.featureplatform.dao;

import com._4paradigm.openmldb.common.Pair;
import com._4paradigm.openmldb.featureplatform.dao.model.*;
import com._4paradigm.openmldb.featureplatform.utils.*;
import com._4paradigm.openmldb.sdk.Column;
import com._4paradigm.openmldb.sdk.Schema;
import com._4paradigm.openmldb.sdk.impl.SqlClusterExecutor;
import com.mysql.cj.x.protobuf.MysqlxCursor;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Repository
public class FeatureServiceService {

    @Autowired
    private Environment env;

    @Autowired
    public FeatureServiceService(Environment env) {
        this.env = env;
    }

    public static FeatureService resultSetToFeatureService(ResultSet resultSet) throws SQLException {
        return new FeatureService(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3),
                resultSet.getString(4), resultSet.getString(5), resultSet.getString(6),
                resultSet.getString(7));
    }

    public List<FeatureService> getFeatureServices() throws SQLException {
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        String sql = "SELECT name, version, feature_names, description, db, sql, deployment FROM SYSTEM_FEATURE_PLATFORM.feature_services";

        ArrayList<FeatureService> featureServices = new ArrayList<>();
        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(sql);
        ResultSet resultSet = openmldbStatement.getResultSet();

        while (resultSet.next()) {
            FeatureService featureService = resultSetToFeatureService(resultSet);
            featureServices.add(featureService);
        }

        return featureServices;
    }

    public static Map<String, String> getLatestServiceNameVersionMap(Connection connection) throws SQLException {
        Map<String, String> latestNameVersionMap = new HashMap<>();

        String sql = "SELECT name, version FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services";
        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(sql);
        ResultSet result = openmldbStatement.getResultSet();

        while (result.next()) {
            String name = result.getString(1);
            String version = result.getString(2);
            latestNameVersionMap.put(name, version);
        }

        return latestNameVersionMap;
    }

    public List<FeatureService> getLatestFeatureServices() throws SQLException {
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        ArrayList<FeatureService> outputFeatureServices = new ArrayList<>();

        Map<String, String> latestNameVersionMap = getLatestServiceNameVersionMap(connection);
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
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        String sql = String.format("SELECT name, version, db, deployment FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services WHERE name='%s'", name);
        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(sql);
        ResultSet resultSet = openmldbStatement.getResultSet();

        ResultSetUtil.assertSizeIsOne(resultSet);

        return resultSetToLatestFeatureService(resultSet);
    }

    public FeatureService getFeatureServiceByNameAndVersion(String name, String version) throws SQLException {
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        String sql = String.format("SELECT name, version, feature_names, description, db, sql, deployment FROM SYSTEM_FEATURE_PLATFORM.feature_services WHERE name='%s' AND version='%s'", name, version);

        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(sql);
        ResultSet resultSet = openmldbStatement.getResultSet();

        ResultSetUtil.assertSizeIsOne(resultSet);
        return resultSetToFeatureService(resultSet);
    }


    public void assertServiceNotExist(FeatureService newFeatureService) throws SQLException {
        List<FeatureService> allFatureServices = getFeatureServices();
        for (FeatureService featureService: allFatureServices) {
            if (featureService.getName().equals(newFeatureService.getName()) &&
                    featureService.getVersion().equals(newFeatureService.getVersion())) {
                throw new SQLException(String.format("The feature service has exists, name: %s and version: %s",
                        featureService.getName(), featureService.getVersion()));
            }
        }
    }

    public FeatureService createFeatureService(FeatureService featureService) throws SQLException {
        assertServiceNotExist(featureService);

        Connection connection = OpenmldbSdkUtil.getConnection(env);
        SqlClusterExecutor sqlExecutor = OpenmldbSdkUtil.getSqlExecutor(env);
        FeatureViewService featureViewService = new FeatureViewService(env);

        String featureSetString = featureService.getFeatureNames();

        String deploymentName = String.format("FEATURE_PLATFORM_%s_%s", featureService.getName(), featureService.getVersion());

        List<String> joinKeys = new ArrayList<>(Arrays.asList(featureService.getJoinKeys().split(",")));
        String mergedSql = FeatureSetUtil.featureSetToSql(sqlExecutor, featureViewService, featureSetString, joinKeys);
        String db = FeatureSetUtil.getDbFromFeatureSet(featureViewService, featureSetString);

        // TODO: Skip index check for compatibility of old OpenMLDB cluster
        String deploySql = String.format("DEPLOY %s OPTIONS (SKIP_INDEX_CHECK=\"TRUE\") %s", deploymentName, mergedSql);

        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(String.format("USE %s", db));
        openmldbStatement.execute(deploySql);

        String newFeatureNames = String.join(",", FeatureSetUtil.featureSetToFeatureNames(featureViewService, featureSetString));

        FeatureService newFeatureService = new FeatureService(featureService.getName(), featureService.getVersion(),
            newFeatureNames, featureService.getDescription(), db, deploySql, deploymentName);

        // Insert into database
        String sql = String.format("INSERT INTO SYSTEM_FEATURE_PLATFORM.feature_services (name, version, feature_names, " +
                "description, db, sql, deployment) values ('%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                newFeatureService.getName(), newFeatureService.getVersion(), newFeatureService.getFeatureNames(),
                newFeatureService.getDescription(), newFeatureService.getDescription(), newFeatureService.getSql(),
                newFeatureService.getDeployment());
        openmldbStatement.execute(sql);

        // Add to latest feature service
        sql = String.format("SELECT count(name) FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services WHERE name='%s'",
                newFeatureService.getName());
        openmldbStatement.execute(sql);
        ResultSet resultSet = openmldbStatement.getResultSet();
        ResultSetUtil.assertSizeIsOne(resultSet);
        resultSet.next();
        if (resultSet.getLong(1) == 0) { // Has no other versions
            sql = String.format("INSERT INTO SYSTEM_FEATURE_PLATFORM.latest_feature_services " +
                    "(name, version, db, deployment) values ('%s', '%s', '%s', '%s')", newFeatureService.getName(),
                    newFeatureService.getVersion(), newFeatureService.getDb(), newFeatureService.getDeployment());
            openmldbStatement.execute(sql);
        }

        openmldbStatement.close();
        return newFeatureService;
    }

    // Deprecated and this function is not used yet
    public FeaturesService createFeatureServiceFromDeployment(FeatureServiceDeploymentRequest request) throws SQLException {
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        String featureServiceName = request.getName();
        String version = request.getVersion();
        String db = request.getDb();
        String deploymentName = request.getDeploymentName();
        String description = request.getDescription();

        Statement openmldbStatement = connection.createStatement();
        if (!db.equals("")) {
            openmldbStatement.execute("USE " + db);
        }

        String sql = String.format("SHOW DEPLOYMENT %s", deploymentName);
        openmldbStatement.execute(sql);

        ResultSet result = openmldbStatement.getResultSet();

        while (result.next()) {
            /*
             ......
             --------------------------
             SQL
             --------------------------
             DEPLOY demo_deploy SELECT
               col
             FROM
               t1
             ;
             --------------------------
             .......
             */
            String resultsetString = result.getString(1);
            String internalSqlString = resultsetString.split("SQL")[1].trim();
            int sqlStartIndex = -1;
            int sqlEndIndex = -1;
            boolean getStartIndex = false;
            for (int i = 0; i < internalSqlString.length(); ++i) {
                if (internalSqlString.charAt(i) != '-') {
                    if (!getStartIndex) {
                        sqlStartIndex = i;
                        getStartIndex = true;
                    }
                }
                if (getStartIndex && internalSqlString.charAt(i) == '-') {
                    sqlEndIndex = i - 1;
                    break;
                }
            }

            if (sqlStartIndex <= 0 || sqlEndIndex <= 0) {
                System.out.println("Fail to parse from SQL result: " + resultsetString);
            }

            // Remove the DEPLOY keyword
            String deploymentSql = internalSqlString.substring(sqlStartIndex, sqlEndIndex).trim();
            String selectSql = deploymentSql.substring(deploymentSql.indexOf(" ", deploymentSql.indexOf(" ") + 1)).trim().replaceAll("[\r\n]+", " ");

            // Create feature view
            // TODO: Handle the duplicated name
            String featureViewName = featureServiceName;
            FeatureViewService featureViewService = new FeatureViewService(this.env);
            // TODO: Do not update feature view and add to database now
            //featureViewService.addFeatureView(new FeatureView(featureViewName, "", db, selectSql));

            // Add to latest feature service
            sql = String.format("SELECT count(name) FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services WHERE name='%s'", featureServiceName);
            openmldbStatement.execute(sql);
            result = openmldbStatement.getResultSet();
            result.next();
            if (result.getLong(1) == 0) { // Has no other versions
                sql = String.format("INSERT INTO SYSTEM_FEATURE_PLATFORM.latest_feature_services (name, version, db, deployment) values ('%s', '%s', '%s', '%s')", featureServiceName, featureServiceName, db, deploymentName);
                openmldbStatement.execute(sql);
            }

            // Create feature service
            String featureList = featureViewName;
            //FeaturesService featureService = new FeaturesService(featureServiceName, version, featureList, db, selectSql, deploymentName, description);
            //return createFeatureService(featureService);
        }

        return null;
    }

    public List<String> getFeatureServiceVersions(String name) throws SQLException {
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        ArrayList<String> versions = new ArrayList<>();

        String sql = String.format("SELECT version FROM SYSTEM_FEATURE_PLATFORM.feature_services WHERE name='%s'", name);

        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(sql);
        ResultSet result = openmldbStatement.getResultSet();
        while (result.next()) {
            String version = result.getString(1);
            versions.add(version);
        }

        return versions;
    }

    public void deleteFeatureService(String name) throws SQLException {
        List<String> versions = getFeatureServiceVersions(name);

        for (String version : versions) {
            deleteFeatureService(name, version);
        }
    }

    public void deleteFeatureService(String name, String version) throws SQLException {
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        Statement openmldbStatement = connection.createStatement();

        FeatureService featureService = getFeatureServiceByNameAndVersion(name, version);

        // Delete the deployment
        openmldbStatement.execute("USE " + featureService.getDb());
        String dropDeploymentSql = String.format("DROP DEPLOYMENT %s", featureService.getDeployment());
        openmldbStatement.execute(dropDeploymentSql);

        // Delete record in database
        String sql = String.format("DELETE FROM SYSTEM_FEATURE_PLATFORM.feature_services " +
                "WHERE name='%s' AND version='%s'", name, version);
        openmldbStatement.execute(sql);

        // Delete if it is the latest version
        if (getLatestVersion(name).equals(version)) {
            sql = String.format("DELETE FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services " +
                    "WHERE name='%s' AND version='%s'", name, version);
            openmldbStatement.execute(sql);
        }

        openmldbStatement.close();
    }

    public void updateLatestVersion(String name, String newVersion) throws SQLException {
        String oldLatestVersion = getLatestVersion(name);

        if (!oldLatestVersion.equals(newVersion)) {
            Connection connection = OpenmldbSdkUtil.getConnection(env);
            Statement openmldbStatement = connection.createStatement();

            String sql = String.format("DELETE FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services " +
                    "WHERE name='%s' AND version='%s'", name, oldLatestVersion);
            openmldbStatement.execute(sql);

            FeatureService featureService = getFeatureServiceByNameAndVersion(name, newVersion);
            sql = String.format("INSERT INTO SYSTEM_FEATURE_PLATFORM.latest_feature_services " +
                    "(name, version, db, deployment) values ('%s', '%s', '%s', '%s')",
                    featureService.getName(), featureService.getVersion(), featureService.getDb(),
                    featureService.getDeployment());
            openmldbStatement.execute(sql);

            openmldbStatement.close();
        }
    }

    public ResponseEntity<String> requestFeatureService(String name, String requestData) throws IOException, SQLException {
        String apiServerEndpoint = env.getProperty("openmldb.apiserver");
        if (apiServerEndpoint == null || apiServerEndpoint.equals("")) {
            throw new IOException("Need to config openmldb.apiserver in application.yml");
        }

        // TODO: Get the db from feature service
        FeatureServiceService featureServiceService = new FeatureServiceService(env);
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
        String apiServerEndpoint = env.getProperty("openmldb.apiserver");
        if (apiServerEndpoint == null || apiServerEndpoint.equals("")) {
            throw new IOException("Need to config openmldb.apiserver in application.yml");
        }

        // TODO: Get the db from feature service
        FeatureServiceService featureServiceService = new FeatureServiceService(env);
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

    public Schema getRequestSchema(String serviceName, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = OpenmldbSdkUtil.getSqlExecutor(env);

        FeatureService featureService = getFeatureServiceByNameAndVersion(serviceName, version);
        String sql = featureService.getSql();
        String db = featureService.getDb();

        List<Pair<String, String>> tables = SqlClusterExecutor.getDependentTables(sql, db,
                OpenmldbTableUtil.getSystemSchemaMaps(sqlExecutor));

        Pair<String, String> mainTablePair = tables.get(0);

        String mainDb = mainTablePair.getKey();
        String mainTable = mainTablePair.getValue();

        Schema schema = sqlExecutor.getTableSchema(mainDb, mainTable);
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
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        String sql = String.format("SELECT version FROM SYSTEM_FEATURE_PLATFORM.latest_feature_services WHERE name='%s'"
                , serviceName);

        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(sql);
        ResultSet resultSet = openmldbStatement.getResultSet();

        ResultSetUtil.assertSizeIsOne(resultSet);
        resultSet.next();

        String version = resultSet.getString(1);
        return version;
    }

    public List<String> getDependentTables(String name, String version) throws SQLException {
        SqlClusterExecutor sqlExecutor = OpenmldbSdkUtil.getSqlExecutor(env);

        FeatureService featureService = getFeatureServiceByNameAndVersion(name, version);
        List<Pair<String, String>> tables = SqlClusterExecutor.getDependentTables(featureService.getSql(),
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
        SqlClusterExecutor sqlExecutor = OpenmldbSdkUtil.getSqlExecutor(env);

        FeatureService featureService = getFeatureServiceByNameAndVersion(serviceName, version);
        String sql = featureService.getSql();
        String db = featureService.getDb();

        Schema schema = SqlClusterExecutor.genOutputSchema(sql, db, OpenmldbTableUtil.getSystemSchemaMaps(sqlExecutor));
        return schema;
    }

    public Schema getOutputSchema(String serviceName) throws SQLException {
        String latestVersion = getLatestVersion(serviceName);
        return getOutputSchema(serviceName, latestVersion);
    }
}