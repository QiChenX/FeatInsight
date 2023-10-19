package com._4paradigm.openmldb.featureplatform.dao;

import com._4paradigm.openmldb.featureplatform.dao.model.*;
import com._4paradigm.openmldb.featureplatform.utils.OpenmldbSdkUtil;
import com._4paradigm.openmldb.featureplatform.utils.ResultSetUtil;
import com._4paradigm.openmldb.sdk.impl.SqlClusterExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OfflineJobService {

    @Autowired
    private Environment env;

    @Autowired
    public OfflineJobService(Environment env) {
        this.env = env;
    }

    public static OfflineJobInfo resultSetToOfflineJobInfo(ResultSet resultSet) throws SQLException {
        return new OfflineJobInfo(resultSet.getInt(1), resultSet.getString(2), resultSet.getString(3),
                resultSet.getTimestamp(4), resultSet.getTimestamp(5), resultSet.getString(6),
                resultSet.getString(7), resultSet.getString(8), resultSet.getString(9));
    }

    public List<OfflineJobInfo> getOfflineJobInfos() throws SQLException {
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        String sql = "SELECT id, job_type, state, start_time, end_time, parameter, cluster, application_id, error " +
                "FROM __INTERNAL_DB.JOB_INFO";

        ArrayList<OfflineJobInfo> offlineJobInfos = new ArrayList<>();

        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(sql);
        ResultSet resultSet = openmldbStatement.getResultSet();

        while (resultSet.next()) {
            OfflineJobInfo offlineJobInfo = resultSetToOfflineJobInfo(resultSet);
            offlineJobInfos.add(offlineJobInfo);
        }

        return offlineJobInfos;
    }

    public OfflineJobInfo getOfflineJobInfo(int id) throws SQLException {
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        String sql = "SELECT id, job_type, state, start_time, end_time, parameter, cluster, application_id, error " +
                "FROM __INTERNAL_DB.JOB_INFO WHERE id = " + id;

        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(sql);
        ResultSet resultSet = openmldbStatement.getResultSet();

        ResultSetUtil.assertSizeIsOne(resultSet);
        resultSet.next();

        return resultSetToOfflineJobInfo(resultSet);
    }

    public String getOfflineJobLog(int id) throws SQLException {
        Connection connection = OpenmldbSdkUtil.getConnection(env);

        String sql = "SHOW JOBLOG " + id;

        Statement openmldbStatement = connection.createStatement();
        openmldbStatement.execute(sql);
        ResultSet resultSet = openmldbStatement.getResultSet();

        ResultSetUtil.assertSizeIsOne(resultSet);
        resultSet.next();

        return resultSet.getString(1);
    }

}