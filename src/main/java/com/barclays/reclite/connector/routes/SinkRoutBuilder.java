package com.barclays.reclite.connector.routes;

import com.barclays.reclite.connector.config.ArrayListAggregationStrategy;
import com.barclays.reclite.connector.config.BatchSizePredicate;
import com.barclays.reclite.connector.config.CsvRecordToSinkBeanMapper;
import com.barclays.reclite.connector.model.SinkBean;
import com.barclays.reclite.connector.model.SinkCsvRecord;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sql.SqlComponent;
import org.apache.camel.dataformat.bindy.csv.BindyCsvDataFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SinkRoutBuilder extends RouteBuilder {
    private static final String QUESTION_MARK = "?";
    private static final String AMPERSAND = "&";
    private static final String COLON = ":";

    @Value("${camel.batch.timeout}")
    private long batchTimeout;

    @Value("${camel.batch.max.records}")
    private int maxRecords;
    @Value("${source.type}")
    private String sourceType;

    @Value("${source.location}")
    private String sourceLocation;

    @Value("${noop.flag}")
    private boolean isNoop;

    @Value("${recursive.flag}")
    private boolean isRecursive;

    @Value("${file.type}")
    private String fileType;

    @Autowired
    private CsvRecordToSinkBeanMapper mapper;

    @Autowired
    DataSource dataSource;
    final String DATA_ID = "DATA_ID";

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean
    public SqlComponent sql(DataSource dataSource) {
        SqlComponent sql = new SqlComponent();
        sql.setDataSource(dataSource);
        return sql;
    }


    @Override
    public void configure() throws Exception {
        final BindyCsvDataFormat bindyCsvDataFormat = new BindyCsvDataFormat(SinkCsvRecord.class);
        bindyCsvDataFormat.setLocale("default");

        from(buildFileUrl())
                .transacted()
                .unmarshal(bindyCsvDataFormat)
                .split(body()).streaming()
                .bean(mapper, "convertAndTransform")
                .process(new Processor() {
                    public void process(org.apache.camel.Exchange xchg) throws Exception {
                     SinkBean sinkBean = xchg.getIn().getBody(SinkBean.class);
                        Map<String, Object> dataMap = new HashMap<>();
                        dataMap.put("code", sinkBean.getDepartmentCode());
                        dataMap.put("name", sinkBean.getDepartmentName());
                        xchg.getIn().setHeader(DATA_ID,dataMap);
                        xchg.getIn().setBody(dataMap);
                    }
                }).aggregate(header(DATA_ID), new ArrayListAggregationStrategy())
            .completionPredicate(new BatchSizePredicate(maxRecords)).to("sql:INSERT INTO department(department_code,department_name) VALUES (:#code,:#name)")
            .completionTimeout(batchTimeout).end();
    }

    private String buildFileUrl() {
        return sourceType + COLON + sourceLocation +
                QUESTION_MARK + "noop=" + isNoop +
                AMPERSAND + "recursive=" + isRecursive +
                AMPERSAND + "include=" + fileType;
    }
}
