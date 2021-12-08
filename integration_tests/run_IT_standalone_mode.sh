    export SPARK_MASTER_HOST=localhost
    export SPARK_MASTER=spark://$SPARK_MASTER_HOST:7077
    $SPARK_HOME/sbin/start-master.sh -h $SPARK_MASTER_HOST
    $SPARK_HOME/sbin/spark-daemon.sh start org.apache.spark.deploy.worker.Worker 1 $SPARK_MASTER

    PYSP_TEST_spark_master=$SPARK_MASTER \
        PYSP_TEST_spark_executor_cores=16 \
      ./run_pyspark_from_build.sh -k test_performance_cast_array_to_string -s 

    $SPARK_HOME/sbin/spark-daemon.sh stop org.apache.spark.deploy.worker.Worker 1
    $SPARK_HOME/sbin/stop-master.sh