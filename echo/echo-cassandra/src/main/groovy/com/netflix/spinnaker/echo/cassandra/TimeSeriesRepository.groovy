package com.netflix.spinnaker.echo.cassandra

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.exceptions.BadRequestException
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.serializers.IntegerSerializer
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.spinnaker.echo.model.Event
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Repository

/**
 * A time series repository for echo events for the last three hours
 */
@Slf4j
@Repository
class TimeSeriesRepository implements ApplicationListener<ContextRefreshedEvent> {

    static final int TTL_DURATION = 10800

    @Autowired
    Keyspace keyspace

    ObjectMapper mapper = new ObjectMapper()

    static ColumnFamily<Integer, String> CF_APPLICATION
    static final String CF_NAME = 'timeseries'

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        CF_APPLICATION = ColumnFamily.newColumnFamily(CF_NAME, IntegerSerializer.get(), StringSerializer.get())
        try {
            runQuery '''select * from events_time_series limit 1;'''
        } catch (BadRequestException ignored) {
            runQuery '''
                CREATE TABLE events_time_series (
                    type text,
                    event_time timestamp,
                    inserted_time timestamp,
                    keys_and_values text,
                    PRIMARY KEY(type,inserted_time)
                )
            '''
        }
    }

    void add(Event event) {
        if (!event.details.type) {
            event.details.type = 'UNKNOWN'
        }

        runQuery """
            INSERT INTO events_time_series(
            type, event_time, inserted_time, keys_and_values)
            VALUES(
                '${event.details.type}',
                ${event.details.created},
                dateof(now()),
                '${mapper.writeValueAsString(event)}'
            ) USING TTL $TTL_DURATION;
        """
    }

    List<Map> eventsByType(String type, long startTime) {
        def result = runQuery """
            SELECT keys_and_values FROM events_time_series WHERE inserted_time >= ${startTime} and type = '${type}';
        """

        result.result.rows.collect {
            mapper.readValue(it.columns.getColumnByName('keys_and_values').stringValue, Map)
        }
    }

    @VisibleForTesting
    private runQuery(String query) {
        keyspace.prepareQuery(CF_APPLICATION).withCql(query).execute()
    }

}
