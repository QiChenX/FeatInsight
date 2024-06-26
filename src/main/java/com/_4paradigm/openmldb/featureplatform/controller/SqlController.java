package com._4paradigm.openmldb.featureplatform.controller;

import com._4paradigm.openmldb.featureplatform.dao.model.OfflineJobInfo;
import com._4paradigm.openmldb.featureplatform.dao.model.OfflineSqlRequest;
import com._4paradigm.openmldb.featureplatform.dao.model.SimpleSqlRequest;
import com._4paradigm.openmldb.featureplatform.dao.model.SqlRequest;
import com._4paradigm.openmldb.featureplatform.service.SqlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/sql")
public class SqlController {
    private static final Logger logger = LoggerFactory.getLogger(FeatureController.class);

    private final SqlService sqlService;

    @Autowired
    public SqlController(SqlService sqlService) {
        this.sqlService = sqlService;
    }

    @PostMapping("/online")
    public ResponseEntity<List<List<String>>> executeOnline(@RequestBody SimpleSqlRequest simpleSqlRequest) throws SQLException {
        try {
            return ResponseEntity.ok(sqlService.executeOnlineSql(simpleSqlRequest.getSql(), simpleSqlRequest.getSparkConfig()));
        } catch (SQLException e) {
            logger.info(String.format("Call executeOnline with %s but get exception: %s", simpleSqlRequest, e.getMessage()));
            throw e;
        }
    }

    @PostMapping("/offline")
    public ResponseEntity<OfflineJobInfo> executeOffline(@RequestBody OfflineSqlRequest offlineSqlRequest) throws SQLException {
        try {
            return ResponseEntity.ok(sqlService.executeOfflineSql(offlineSqlRequest.getSql(), offlineSqlRequest.getSparkConfig()));
        } catch (SQLException e) {
            logger.info(String.format("Call executeOffline with %s but get exception: %s", offlineSqlRequest, e.getMessage()));
            throw e;
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<String> validateSql(@RequestBody SimpleSqlRequest simpleSqlRequest) throws SQLException {
        try {
            List<String> result = sqlService.validateSql(simpleSqlRequest.getSql());
            if (result.size() == 0) {
                return new ResponseEntity<>("Success to validate sql", HttpStatus.OK);
            } else {
                return new ResponseEntity<>(String.join(",", result), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (SQLException e) {
            logger.info(String.format("Call validateSql with %s but get exception: %s", simpleSqlRequest, e.getMessage()));
            throw e;
        }
    }

    @PostMapping("/import")
    public int importSql(@RequestBody SqlRequest sqlRequest) throws SQLException {
        try {
            int jobId = sqlService.importData(sqlRequest.getSql(), sqlRequest.isOnline(), sqlRequest.getSparkConfig());
            return jobId;
        } catch (SQLException e) {
            logger.info(String.format("Call importSql with %s but get exception: %s", sqlRequest, e.getMessage()));
            throw e;
        }
    }

}