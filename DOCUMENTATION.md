![](kafkistry-web/src/main/resources/ui/static/img/default/banner.png)

# Kafkistry documentation


## Contents

 - [Repository for metadata](#kafkistry-repository)
 - [Configuration properties](#configuration-properties)
 - [Configure repository](#configure-repository)
   + [Regular Dir](#using-regular-dir-repository)
   + [Git backed](#using-git-backed-repository)
 - [Connecting to kafka properties](#connecting-to-kafka-properties)   
   + [AdminClient properties](#adminclient-properties)
   + [Adding kafka cluster(s)](#add-kafka-cluster-to-kafkistry)
   + [Operation timeouts](#operation-timeouts)
 - [Kafka version compatibility](#kafka-version-compatibility)
 - [Configure metadata pooling/scraping](#pooling-metadata-from-clusters)
 - [Enabling/disabling specific clusters](#enablingdisabling-specific-clusters)
 - [Customizing web app](#customizing-web-app)
 - [Filter options](#filter-options)
 - [Prometheus metrics](#prometheus-metrics)
   + [JVM default metrics](#jvm-metrics)
   + [Kafkistry operating metrics](#kafkistry-operating-metrics)
   + [Consumers lag metric](#consumer-lag)
   + [Topic offsets](#topic-offsets-metrics)
   + [Topic retention](#topic-retention-metrics)
 - [Security / Users](#security-for-web---kafkistry-users)
   + [Static users list](#static-users-list)
   + [Custom authentication provider](#implementing-custom-authentication-provider)
   + [Owner - user groups](#owner---user-groups)
   + [Static owners list](#static-owner-groups)
 - [High availability setup](#high-availability-ha-setup---multiple-kafkistry-nodes)
   + [Hazelcast](#hazelcast)
 - [Customizing topic creation wizard](#customizing-topic-wizard)
 - [Reading/consuming records utility](#consumingreading-records-limits)
 - [Oldest record age sampling](#oldest-record-age-samplinganalysis)
 - [Record structure analysis](#record-structure-analysis)
 - [Scraping broker's disk capacity](#scraping-brokers-disk-capacity)
   + [Static/hardcoded values](#static-hard-coded-total-capacity)
   + [Disk capacity from Prometheus](#obtaining-disk-size-and-free-space-from-prometheus) 
   + [Custom implementation for disk metrics](#custom-implementation-for-fetching-broker-disk-metrics)
 - [KStream detection](#kstreams-detection)
 - [Topic inspection](#topic-inspection)
   + [Extra topic inspectors](#extra-topic-inspectors)
   + [Topic rule violations](#topic-rule-violations)
 - [ACLs inspection](#acls-inspection)
 - [Cluster inspection](#cluster-inspection)
 - [SQL querying - SQLite](#sql-metadata-querying---sqlite)
   + [ClickHouseDB-like API](#clickhousedb-like-rest-api)
 - [Autopilot](#autopilot)
 - [Slack integration](#slack-integration---auditing)
 - [Jira integration](#jira-integration)
 - [Miscellaneous UI Settings](#miscellaneous-ui-settings)
 - [Logging](#logging)
   + [Console output](#logging-to-console-output)
   + [Files](#logging-to-files)
   + [Logstash](#logging-to-logstash)
 - [Writing custom plugins](#writing-custom-plugins)
 


## Kafkistry repository

Repository represents source of truth for expected/wanted topics, ACLs and Entity-quotas.
It contains following entity types:

| Entity             | Breakdown                | Description                                                            |
|--------------------|--------------------------|------------------------------------------------------------------------|
| **Kafka clusters** | File per kafka cluster   | Kafkistry will use those to know which clusters to connect to          |
| **Topics**         | One file per topic name  | Configuration of topic with possible overrides per specific clusters   |
| **ACLs**           | One file per principal   | List of expected ACLs for principal                                    |
| **Quota entities** | One file per user/client | Amount of operational quotas for clients and users connecting to kafka |

Default repository's storage implementation is GIT repository containing `*.yaml` files.

By default, when update is made through UI, Kafkistry will create a new branch, commit changes and push it to upstream.
That branch can then be merged through pull request on remote side and Kafkistry will fetch it on next poll.

Kafkistry can also work with GIT repo without having remote/origin specified. In that case, commits will be made but without pushing to upstream.


## Configuration properties

There is [application-defaults.yaml](kafkistry-app/src/main/resources/application-defaults.yml) 
which has definitions of some default properties and defines alias/alternative property names to be used.

Kafkistry is a Spring Boot application using its configuration properties processors which allows multiple ways for 
properties being passed in. 
(see more info here [spring boot externalizing configuration docs](https://docs.spring.io/spring-boot/docs/2.0.6.RELEASE/reference/html/boot-features-external-config.html))

Each configuration property has its full name, for example:
`app.record-analyzer.executor.concurrency`, this specific example is defined in application-defaults.yaml as:
```yaml
app.record-analyzer.executor.concurrency: ${RECORD_ANALYZER_CONCURRENCY:1}
```
Which basically adds alternative/alias property name as well as default value being `1`.

Spring framework allows this property to be defined in following ways (not exhaustive list):

| Mechanism                | Examples, from lowest to highest precedence        |
|--------------------------|----------------------------------------------------|
| in application.yaml      | `RECORD_ANALYZER_CONCURRENCY: 5`                   |
| as environment variable  | `export RECORD_ANALYZER_CONCURRENCY=5`             |
| as JVM option            | `-DRECORD_ANALYZER_CONCURRENCY=5`                  |
| as command line argument | `--RECORD_ANALYZER_CONCURRENCY=5`                  |
| in application.yaml      | `app.record-analyzer.executor.concurrency: 5`      |
| as environment variable  | `export APP_RECORDANALYZER_EXECUTOR_CONCURRENCY=5` |
| as JVM option            | `-Dapp.record-analyzer.executor.concurrency=5`     |
| as command line argument | `--app.record-analyzer.executor.concurrency=5`     |

Same applies for all possible properties Kafkistry is configurable by.

**NOTE**: Only some properties have defined shorter alias in application.yaml.



## Configure repository

There are two modes/implementations of repository:
 - Regular directory `dir` storage which simply read/writes `*.yaml` files in local directory.
 - Git directory `git` storage which is exact format to simple `dir` implementation except that it's backed by GIT

### Using regular `dir` repository

- activate `dir` spring profile with environment variable `SPRING_PROFILES_ACTIVE=dir`
- specify desired path with env variable `APP_REPOSITOY_DIR_PATH=/desired/path` 
  (default value is `CURRENT_WORK_DIR/kafkistry/repository`)
  
This implementation is basic and does not support showing history and remote persistence (in contrast to git)
  
### Using `git` backed repository

This implementation is default and preferred to simple `dir`.
Kafkistry treats contents of main/master branch as source of truth.

Git allows writing changes and committing them to separate branch, which then could be reviewed 
through PR (pull-request) on remote side (GitHub/Bitbucket/Gitlab/...)

Example use case:
 - Somebody within your company wants to create new topic
 - He/she could then create new topic's description through Kafkistry's UI and submit it
 - Kafkistry will make commit in new branch and push it to remote
 - This person then creates a PR for administrator to review
 - Why review matters? For example: 
    + you may want to check that topic name conforms your naming conventions; 
    + number of partitions this person specified might be huge; 
    + specified `retention.bytes` might be beyond available disk space on kafka brokers
    + ..._list could go on_
 - PR gets approved/merged
 - Kafkistry fetches newly merged changes and displays that this new topic is expected to exist but it's missing
 - administrator then simply creates this missing topic

Properties:

| Property                                          | Description                                                                                                                                                                                                                                  |
|---------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GIT_REMOTE_SSH_URI`                              | URI of remote git dir to clone/use. For example `ssh://kr-user@git-host:1234/my-kafkistry-repo.git`. It can be omitted, in this case Kafkistry will simply initialize empty repository without remote, and commits won't be pushed anywhere. |
| `LOCAL_GIT_DIR`                                   | Path to directory where will Kafkistry clone/init git repository                                                                                                                                                                             |
| `GIT_MAIN_BRANCH`                                 | Default value: `master`                                                                                                                                                                                                                      |
| `GIT_SSH_PASSWORD`                                | Password to use to authenticate `kr-user` as ssh client. Can be omitted if private key s used.                                                                                                                                               |
| `SSH_PRIVATE_KEY`                                 | Literal value of ssh private key. Example how to generate keypair: `ssh-keygen -t rsa -C "my kafkistry key" -m PEM`, add public key to your git repo server                                                                                  |
| `SSH_PRIVATE_KEY_PATH`                            | Path to private key file, alternative to `SSH_PRIVATE_KEY`                                                                                                                                                                                   |
| `SSH_PRIVATE_KEY_PASS`                            | Passphrase for given private key, can be omitted if private key has a passphrase                                                                                                                                                             |
| `GIT_TIMEOUT_SEC`                                 | Timeout for git remote calls (push/pull). Default `30` sec                                                                                                                                                                                   |
| `GIT_REFRESH_INTERVAL_SEC`                        | Git periodic fetch polling interval. Default `30` sec                                                                                                                                                                                        |
| `app.repository.git.strict-ssh-host-key-checking` | Default `false` (ssh would fail if current host never connected to remote and having no saved fingerprint of remote host)                                                                                                                    |
| `GIT_BROWSE_BRANCH_BASE_URL`                      | Optional. Used for UI generating links for external git repository browsing for showing branch. Example value: `https://my-bitbucket/projects/my-project/repos/my-repo/compare/commits?sourceBranch=`                                        |
| `GIT_BROWSE_COMMIT_BASE_URL`                      | Optional. Used for UI generating links for external gir repository browsing for showing commit. Example value: `https://my-bitbucket/projects/my-project/repos/my-repo/commits/`                                                             |
| `GIT_COMMIT_TO_MASTER_BY_DEFAULT`                 | Should Kafkistry commit directly to main/master branch. Default `false`                                                                                                                                                                      |



## Connecting to kafka properties

By default, there is no need to set up any special properties for Kafkistry to use when connecting to kafka via `AdminClient`
unless, your kafka cluster(s) require non PLAINTEXT connections.

Kafkistry stores list of kafka clusters which it needs to connect to in it's [repository](#configure-repository), 
and for security reasons. It's by design that no additional properties are stored there except `bootstrap.servers` 
and which security protocol to use.

### **AdminClient** properties

Any additional property for Kafkistry to use when connecting to kafka can be passed by environment variables 
with name prefixed by `APP_KAFKA_PROPERTIES_*`.

Few examples:

| AdminClient property      | Environment variable name to pass by                |
|---------------------------|-----------------------------------------------------|
| `ssl.truststore.location` | `AP<br/>P_KAFKA_PROPERTIES_SSL_TRUSTSTORE_LOCATION` |
| `sasl.mechanism`          | `APP_KAFKA_PROPERTIES_SASL_MECHANISM`               |
| `sasl.jaas.config`        | `APP_KAFKA_PROPERTIES_SASL_JAAS_CONFIG`             |

Any defined property like that will be used by Kafkistry to connect to all kafka clusters.

In case when there is need to connect to different kafka clusters with different properties then _"profiles"_ come into play. 
You can specify different properties per different profile by specifying environment variable with name of 
following pattern: `KAFKA_PROFILES_<PROFILE-NAME>.PROPERTIES_<KAFKA_PROPERTY>`

**NOTE**: when passing properties by environment variable `PROFILE_NAME` can't contain `_`

Few examples:

| Profile | AdminClient property | Environment variable name to pass by                 |
|---------|----------------------|------------------------------------------------------|
| `foo`   | `sasl.mechanism`     | `APP_KAFKA_PROFILES_FOO_PROPERTIES_SASL_MECHANISM`   |
| `foo`   | `sasl.jaas.config`   | `APP_KAFKA_PROFILES_FOO_PROPERTIES_SASL_JAAS_CONFIG` |
| `bar`   | `sasl.mechanism`     | `APP_KAFKA_PROFILES_BAR_PROPERTIES_SASL_MECHANISM`   |
| `bar`   | `sasl.jaas.config`   | `APP_KAFKA_PROFILES_BAR_PROPERTIES_SASL_JAAS_CONFIG` |

**NOTE**: those kafka properties profiles have nothing to do with Spring framework's profiles

**NOTE**: if you plan to pass `sasl.jaas.config` via docker's environment variable, 
make sure it's one-liner value because docker's `-e` and `--env-file` does not handle multi-lined values properly

### Add kafka cluster to Kafkistry

In order to Kafkistry to know about which kafka clusters to connect to, kafka cluster needs to be added to Kafkistry 
via UI (or manually adding new file in Kafkistry's [repository](#configure-repository)).

When adding cluster, security protocol needs to be chosen: 

| SSL | SASL | Corresponding `security.potocol` |
|-----|------|----------------------------------|
| no  | no   | `PLAINTEXT`                      |
| yes | no   | `SSL`                            |
| no  | yes  | `SASL_PLAINTEXT`                 |
| yes | yes  | `SASL_SSL`                       |

When adding new cluster or editing metadata of existing, specific profile(s) can be selected to include 
properties defined for that particular profile.

After adding cluster to Kafkistry's repository (and merging addition to master in case of GIT repository),
Kafkistry will start to periodically scrape metadata from it.

### Operation timeouts

Every interaction with kafka could be treated as _read_ or _write_ operation.
You can define different timeout for each of those.

| Property                 | Default         | How much will Kafkistry wait for AdminClient's `Future` to complete                                      |
|--------------------------|-----------------|----------------------------------------------------------------------------------------------------------|
| `KAFKA_READ_TIMEOUT_MS`  | `15000` (15sec) | Applies to API calls which have read semantics, i.e. don't actively change state (mostly DESCRIBE calls) | 
| `KAFKA_WRITE_TIMEOUT_MS` | `120000` (2min) | Applies to API calls which have write semantics, i.e. CREATING/DELETING/ALTERING/... operations          | 

### Kafkistry's permissions on Kafka cluster

**NOTE**: If your kafka cluster(s) does not use security restrictions following section is not important.

Even though, Kafkistry is administrative tool, it's just yet another client connecting to kafka, and it is subject for kafka's
`Authorizer` to deny some operations Kafkistry want's to perform.

The simplest setup is to specify Kafkistry in `super.users` configuration property of all kafka brokers.
This way will allow Kafkistry to always _"see"_ all topics/groups/etc.... Being super user means that kafka will permit all
requests coming from Kafkistry.

Things to know is adding Kafkistry in `super.users` sounds too scary:
 - Kafkistry periodically does only READ-like operations such as describing topics, groups, configs, fetching topic offsets...
 - active WRITE-like operations are performed **only by manual action** of ADMIN user through UI (or API call to be more precise)
   +  WRITE-lie operations would be creations/deletions/altering configs/etc

**NOTES** on approach where Kafkistry is not being super-user:
 - functionality Kafkistry won't have permission (ACL) to perform simply won't work
 - some statuses might be wrong, for example: a topic might be declared as MISSING, even though it exists but Kafkistry does not have permission to describe it



## Kafka version compatibility 

There are tests for all interactions that Kafkistry does with kafka cluster.

| Kafka Version     | Compatibility             | Need connect to ZK for some operations |
|-------------------|---------------------------|----------------------------------------|
| v > `3.3`         | full?  _(not tested yet)_ | no                                     |
| `2.6` ≤ v ≤ `3.3` | full                      | no                                     |
| `2.1` ≤ v < `2.6` | full                      | yes                                    |
| `1.0` ≤ v < `2.1` | partial _(not tested)_    | yes                                    |

Kafkistry determines major/minor version of kafka by looking at controller's `inter.broker.protocol.version` config property.
This may not reflect an actual build version of broker, but it will mean actual version is greater or equal.
It's done this way to avoid need to connect to zookeeper if not needed.

Based on detected version, Kafkistry will perform interactions/operations either by leveraging API calls from `AdminClient`
or going to Zookeeper if needed depending upon detected kafka cluster version.

**NOTE**: secure connection setup to Zookeeper is not tested!



## Pooling metadata from clusters

Kafkistry will periodically pool/scrape metadata from all kafka clusters that it connects onto.
There are several properties that can be configured.
Scraping is broken to several **job** types, for example:
 - `cluster_state` - to describe cluster, topics, acls
 - `consumer_groups` - to describe consumer groups and their assignments and offsets
 - `topic_offsets` - to get begin and end offset of each topic-partition
 - `client_quotas` - to describe client quotas
 - ...

| Property                                | Default                 | Description                                                                                               |
|-----------------------------------------|-------------------------|-----------------------------------------------------------------------------------------------------------|
| `CLUSTER_POOL_INTERVAL`                 | `10000` (ms)            | How often to scrape metadata (topics, consumer groups, ACLs, ...)                                         |
| `CLUSTER_POOL_RECORD_SAMPLING_INTERVAL` | `120000` (ms)  _(2min)_ | How often to read oldest and/or newest messages from every topic-partition                                |
| `CLUSTER_POOL_CONCURRENCY`              | `6`                     | Limit for one type of **job** (e.g. topic_offsets fetching) for how many clusters to process concurrently |
| `CLUSTER_POOL_CONCURRENCY_PER_CLUSTER`  | `30`                    | How many client's (admin+consumer) to have opened toward one cluster                                      |



## Enabling/disabling specific clusters

There is option to completely disable or enable connecting to specific kafka clusters added to 
Kafkistry repository.

Disabling specific clusters is useful in situations when you have more than one environment, 
for example having _production_ environment and _staging_ environment, which are unreachable 
on network level. Suppose you have common GIT repository for managing topic configurations
on clusters from both environments. Deployment of Kafkistry on _staging_ environment could not reach
kafka clusters in _production_ environment and vice-versa. 

Example below shows that `KR-PRRD` can reach `KFK-P1` and `KFK-P2`, but can't `KFK-S1` and `KFK-S2`,
`KR-STAG` can reach `KFK-S1` and `KFK-S2`, but can't `KFK-P1` and `KFK-P2`,
```
           |---------->[GIT]<----------|     
           |                           |    
           |                           |    
   |---[KR-PROD]---|           |---[KR-STAG]---|  
   |               |           |               |  
   |               |           |               |  
   v               v           v               v   
[KFK-P1]        [KFK-P2]    [KFK-S1]       [KFK-S1] 
```
So, it's better to simply disable particular clusters when you know they are unreachable.

| Property            | Default | Description                                                                                                                                           |
|---------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ENABLED_CLUSTERS`  | _any_   | Comma separated list of cluster identifiers to allow attempt to connect to. Can be literal `*`                                                        |
| `DISABLED_CLUSTERS` | _none_  | Comma separated list of cluster identifiers to prevent attempt to connect to. Can be literal `*`                                                      |
| `ENABLED_TAGS`      | _any_   | Comma separated list of cluster tags to allow attempt to connect to. Matches when any of cluster tags matches tag from this list. Can be literal `*`  |
| `DISABLED_TAGS`     | _none_  | Comma separated list of cluster tags to prevent attempt to connect to. Blocks when any of cluster tags matches tag from this list. Can be literal `*` |



## Customizing web app

| Property                        | Default      | Description                                                                                                                                                                                       |
|---------------------------------|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `HTTP_PORT`                     | `8080`       | Which port should web server listen on. No need to change if running in docker with port mapping.                                                                                                 |
| `HTTP_ROOT_PATH`                | `/kafkistry` | This is basically _prefix_ for all http urls paths. Useful if Kafkistry is deployed behind http proxy which will be using url path for routing. **NOTE**: must not end with `/` but can be empty. |
| `HTTP_REQUESTS_LOGGING_ENABLED` | `true`       | Should each HTTP request to Kafkistry be logged, see [filter](kafkistry-web/src/main/kotlin/com/infobip/kafkistry/webapp/RequestLoggingFilterConfig.kt)                                           |



## Filter options

Various features in Kafkistry can be enabled/disabled for specific cluster/topic/consumer/etc
This section describes such options, this section is referenced by other sections using filtering.

| Property            | Default  | Description             |
|---------------------|----------|-------------------------|
| `<prefix>.included` | _(all)_  | Which values to include |
| `<prefix>.excluded` | _(none)_ | Which values to exclude |

Each of those properties can be:
 - `*` - matches all
 - _undefined_
   - inclusion matches all
   - exclusion matches none
 - CSV list
   - example: `my-cluster-1,my-cluster-2`
 - List of strings:
   - example: 
    ```yaml
   - "my-topic-1"
   - "my-topic-2"
    ```

### Cluster filter options

| Property                        | Default  | Description                                                                                |
|---------------------------------|----------|--------------------------------------------------------------------------------------------|
| `<prefix>.identifiers.included` | _(all)_  | Which cluster identifiers to include. Can be omitted, `*`, CSV string or list of strings.  |
| `<prefix>.identifiers.excluded` | _(none)_ | Which cluster identifiers to exclude. Can be omitted, `*`, CSV string or list of strings.  |
| `<prefix>.tags.included`        | _(all)_  | Clusters having which tags to include. Can be omitted, `*`, CSV string or list of strings. |
| `<prefix>.tags.excluded`        | _(none)_ | Clusters having which tags to exclude. Can be omitted, `*`, CSV string or list of strings. |

   
### Example of properties in yaml
````yaml
<prefix-for-clusters>:
  included: "cluster-1,cluster-2"
<prefix-for-topics>:
  excluded:
   - "topic-2"
````
will result in following acceptance matrix:

| Filter matches | `topic-1` | `topic-2` | `topic-3` |
|----------------|-----------|-----------|-----------|
| `cluster-1`    |   yes     |    no     |   yes     |          
| `cluster-2`    |   yes     |    no     |   yes     |  
| `cluster-3`    |    no     |    no     |    no     |  



## Prometheus' metrics

Kafkistry is capable of exporting metrics for prometheus.
It exports some metrics about itself and metrics about consumer's lag.

By default, metrics can be scraped on `/kafkistry/prometheus/metrics` HTTP endpoint.

Path is configurable by: `HTTP_ROOT_PATH` (default=`/kafkistry`) and `PROMETHEUS_PATH` (default=`/prometheus/metrics`)

### JVM metrics

Registration of default JVM metrics is done using `io.prometheus.client.hotspot.DefaultExports`

### Kafkistry operating metrics
 
There are numerous metrics showing how is Kafkistry operating. 
(UI requests latencies, API request latencies, time since last kafka metadata scrape, etc.)

All Kafkistry-specific metrics have default naming starting with `kafkistry_`*.
This prefix can be configured via `APP_METRICS_PREFIX` property.

### Consumer lag

Monitoring lag of consumers is important aspect of whole system.

Definition of consumer lag is difference between the latest offset in topic-partition and 
last committed offset  by consumer.

**Lag** represents how many messages is consumer _lagging_ from producer(s).

```
Topic-partition:
----+---+---+---+---+---+-----+---+---+---+
... |   |   | C |   |   | ... |   |   | E |
----+---+---+---+---+---+-----+---+---+---+
              |                         |
              |<--------- LAG --------->|
              
C - committed offset by consumer
E - end offset (latest produced)
```
Ideally, you want for lag be `0` or oscillate slightly above zero.
Steadily growing lag means that consumer can't keep up with producer's rate.

Available configuration properties

| Property                                                                     | Default | Description                                                                                        |
|------------------------------------------------------------------------------|---------|----------------------------------------------------------------------------------------------------|
| `app.metrics.consumer-lag.enabled`                                           | `true`  | Enable/disable exporting lag metric at all                                                         |
| `app.metrics.consumer-lag.enabled-on.clusters.<...filter options...>`        | _all_   | For which clusters to include consumer lag metric. See [filtering options](#filter-options)        |
| `app.metrics.consumer-lag.enabled-on.topics.<...filter options...>`          | _all_   | For which topics to include consumer lag metric. See [filtering options](#filter-options)          |
| `app.metrics.consumer-lag.enabled-on.consumer-groups.<...filter options...>` | _all_   | For which consumer groups to include consumer lag metric. See [filtering options](#filter-options) |

#### Why using Kafkistry metric for lag if `KafkaConsumer.metrics()` already has metric for lag?

There are several drawbacks with approach that every consumer exports metric about self-lag.
 - It's ok if some members from consumer group go down, group will simply rebalance and you'll still have lag metric 
   form other members which are up and running. What if all consumer members go down? Then you won't have lag metric at all.
 - Every consumer member needs to export it's metric for lag. Also, it needs to be implemented individually for each language that's
   being used for services consuming from kafka.
 - Kafkistry offers unified and centralized place for collecting and exporting lag for all consumers on all clusters regardless of
   language consumers are written in, with having lag even if consumer members are down.

Downsides:
 - Kafkistry will introduce slight timed delay for lag metric depending on rate of pooling/scraping topic offsets and latest consumer's commit offsets
 - Kafkistry will "see" consumer having lag until it performs commit, lag might be shown as greater that actually is in cases 
   when consumer is configured to perform time or count periodic commits comparing to performing commits after each processed record/batch
 - If some service/consumer group is decommissioned/stopped being used Kafkistry will continue exporting lag until consumer group is explicitly deleted 
   or when retention on topic `__consumer_offsets` deletes the newest commits of that particular group


Kafkistry exports lag as GAUGE metric named `kafkistry_consumer_lag` broken down by following labels:
  - `cluster` - on which kafka cluster it is (one of clusters added into Kafkistry).
    This can be customized by implementing [ClusterLabelMetricProvider](kafkistry-prometheus-metrics-export/src/main/kotlin/com/infobip/kafkistry/metric/ClusterLabelMetricProvider.kt)
    See more about [writing custom plugins](#writing-custom-plugins)
  - `consumer_group` - consumer group ID 
  - `topic` - topic name
  - `partition` - which partition of topic 
  - `consumer_host` - hostname/ip of consumer group's member instance or literal `unassigned`

Value of metric is `lag = end_offset - committed_offset`

Example of consumer lag metric sample: 
```bash
kafkistry_consumer_lag{cluster="my-kafka",consumer_group="my-consumer",topic="my-topic",partition="0",consumer_host="/127.0.0.1",} 123.0
```

### Topic offsets metrics

Kafkistry can export topic partition end (latest) and begin (oldest) offset as GAUGE metrics:
  - `kafkistry_topic_begin_offset`
  - `kafkistry_topic_end_offset`

Both of those have the following labels:
   - `cluster` - on which kafka cluster it is (one of clusters added into Kafkistry).
     This can be customized by implementing [ClusterLabelMetricProvider](kafkistry-prometheus-metrics-export/src/main/kotlin/com/infobip/kafkistry/metric/ClusterLabelMetricProvider.kt)
     See more about [writing custom plugins](#writing-custom-plugins)
   - `topic` - topic name
   - `partition` - which partition of topic

Available configuration properties

| Property                                                                       | Default | Description                                                                                                  |
|--------------------------------------------------------------------------------|---------|--------------------------------------------------------------------------------------------------------------|
| `app.metrics.topic-offsets.enabled`                                            | `true`  | Enable/disable exporting topic partition offsets metric at all                                               |
| `app.metrics.topic-offsets.enabled-on.clusters.<...cluster filter options...>` | _all_   | For which clusters to include topic offsets metric. See [cluster filtering options](#cluster-filter-options) |
| `app.metrics.topic-offsets.enabled-on.topics.<...filter options...>`           | _all_   | For which topics to include topic offsets metric. See [filtering options](#filter-options)                   |

Use cases:
 - rate of increase of `kafkistry_topic_end_offset` directly corresponds to produce rate
 - difference between _end offset_ and _begin offset_ for particular topic's partition roughly corresponds to 
   number of messages in that partition.
   - **NOTE 1**: this number will be completely wrong for topics with `cleanup.policy` including `compact`
   - **NOTE 2**: this number might be slightly bigger than actual in case of transaction usage due to transaction markers _"using"_ some offsets

### Topic retention metrics

Retention metrics answer the question:
 - _is the topic over-provisioned or under-provisioned in regard to topic's configured `retention.bytes` and `retention.ms`?_

Motivation:
 - you may find yourself in situation to create topic with appropriate configuration for load you expect initialy
 - after some time, traffic increases, and partitions of your topic are no longer truncated by `retention.ms`, but by `retention.bytes` instead
 - this renders your topic not having time retention that you wanted initially

There are 3 metrics:
 - `kafkistry_topic_effective_retention_ms` 
   - GAUGE which shows age of the oldest record in topic partition 
   - **NOTE**: works only if [oldest record age sampling](#oldest-record-age-samplinganalysis) is enabled
   - value represents difference between `Time.now` and `oldestRecord.time` in milliseconds
   - **NOTE**: if records are produced with custom supplied timestamp which is way lower than current time when produce is happening
     then this metric may be completely unusable
 - `kafkistry_topic_size_retention_usage`
   - GAUGE of ratio between actual size of partition against configured `retention.bytes`
   - value `0.0` means that partition is empty
   - value `0.5` means that partition uses 50% of configured `retention.bytes`
   - value can be greater than `1.0` due to how kafka rolls partition segments 
 - `kafkistry_topic_time_retention_usage`
   - GAUGE of ratio between _the oldest record age_ (_effective retention_) of partition against configured `retention.ms`
   - value `0.5` means that partition _uses_ 50% of configured `retention.ms`
   - value can be greater than `1.0` due to how kafka rolls partition segments
   
Different regime cases:
 - **low** time_retention_usage and **high** size_retention_usage
   - topic is truncated by size retention (might want alert on this)
 - **high** time_retention_usage and **low** size_retention_usage
   - topic is truncated by time retention

Available configuration properties

| Property                                                                 | Default | Description                                                                              |
|--------------------------------------------------------------------------|---------|------------------------------------------------------------------------------------------|
| `app.metrics.topic-retention.enabled`                                    | `true`  | Enable/disable exporting topic partition retention metric at all                         |
| `app.metrics.topic-retention.enabled-on.clusters.<...filter options...>` | _all_   | For which clusters to include retention metric. See [filtering options](#filter-options) |
| `app.metrics.topic-retention.enabled-on.topics.<...filter options...>`   | _all_   | For which topics to include retention metric. See [filtering options](#filter-options)   |



## Security for web - Kafkistry users

Security / login when accessing UI, and it's enabled by default.
Security is all about authenticating _User_ which is accessing Kafkistry and resolving its authorities.

Any unauthenticated user accessing UI will be redirected to `/login` page view. 
Kafkistry uses session cookie named `KRSESSIONID` to pass it with each HTTP request so that back-end _knows_ which user is performing each request.

There are several options how authentication can be accomplished.

### Static users list

This is the simplest way to give Kafkistry list of users do the authentication.
It is _static_ in sense that Kafkistry will read the list on startup and any change (for example, adding new user) will require 
Restart of Kafkistry.

Options to pass in the users list.
 - `USERS_PASSWORDS`
    + one user format: `<username>|<password>|<name>|<surname>|<email>|<role>|<token>`
      * `username` - username to login with
      * `password` - plaintext password to login with
      * `name`, `surname`, `email` - displaying it in the UI, in case when using GIT repository, this user metadata
        will be used as **author** of commit
      * `role` - used for resolving which authorities user will have
         - available: `ADMIN`, `USER`, `READ_SERVICE` see more about which authorities each role has in [UserRole](kafkistry-security/src/main/kotlin/com/infobip/kafkistry/webapp/security/model.kt)
      * `token` 
        - optional token which can be used to authenticate HTTP requests by being passed via
          `X-Auth-Token` instead of standard session based approach with authentication via /login page form
    + multiple users can be separated either by new line `\n` or by `;`
    + example with 2 users separated by `;`:
       - `USERS_PASSWORDS=u1|p1|Foo|Bar|u1@kr|ADMIN|atoken;u2|p2|Fizz|Buzz|u2@kr|USER|`
    + this option is great for quick startup, but it is not recommended for production usage
      if its being passed by Kafkistry argument option or docker environment variable because of passing plaintext passwords
 - `USERS_PASSWORDS_YAML` - value holding yaml
   + holds same information as raw `USERS_PASSWORDS` but in yaml format and with encrypted/hashed password BCrypt algorithm
   + `password` can be encrypted with BCrypt or can be in plaintext
   + `attributes` is `String` to `Any` map which is not used by core kafkistry but 
     it is there to allow custom plugins to store additional information within `User` object
   + example:
```yaml
- user:
   username: "admin"
   firstName: "Admy"
   lastName: "Adminsky"
   email: "admin@kr.local"
   role:
     name: "ADMIN"
     authorities:
     - name: "VIEW_DATA"
     - name: "REQUEST_CLUSTER_UPDATES"
     - name: "REQUEST_TOPIC_UPDATES"
     - name: "REQUEST_ACL_UPDATES"
     - name: "REQUEST_QUOTA_UPDATES"
     - name: "MANAGE_GIT"
     - name: "MANAGE_KAFKA"
     - name: "MANAGE_CONSUMERS"
     - name: "READ_TOPIC"
   attributes: {}
 token: null
 password: "$2a$10$xL07zCVsDMgK2FlB0SUiz..GwkmDVf2pnB2kO1G49/zJhWsxo09cm"
 passwordEncrypted: true
# more users...
```
 - `USERS_PASSWORDS_YAML_FILE` - path to file holding yaml
   + same format as `USERS_PASSWORDS_YAML`
   
**NOTE/TIP**: any change in user's list will require restart of Kafkistry

**NOTE/TIP**: If you specify both `USERS_PASSWORDS` and `USERS_PASSWORDS_YAML_FILE` pointing to 
non-existing or empty file, Kafkistry will then write users from `USERS_PASSWORDS` into `USERS_PASSWORDS_YAML_FILE` in 
yaml format with encrypted passwords.

### Implementing custom authentication provider

Custom authentication can be achieved by implementing 
[KafkistryUsernamePasswordAuthProvider](kafkistry-security/src/main/kotlin/com/infobip/kafkistry/webapp/security/auth/providers/KafkistryAuthProvider.kt).
This is mechanism which implements classic username+password authentication. The implementation could perform whatever remote
call to validate credentials and retrieve metadata for [User](kafkistry-security/src/main/kotlin/com/infobip/kafkistry/webapp/security/model.kt)

Second option is to implement [PreAuthUserResolver](kafkistry-security/src/main/kotlin/com/infobip/kafkistry/webapp/security/auth/preauth/PreAuthUserResolver.kt).
This might be useful in situation if Kafkistry will be behind some proxy which will be responsible for login and which will set some
cookie in browser. Then, the custom PreAuthUserResolver could read this cookie and make some remote lookup to validate it and actually
login the user in Kafkistry without user ever seeing login page.

See more about [writing custom plugins](#writing-custom-plugins)

### Owner - user groups

Entities like **Topic**, **Principal**, **Quote Entity** have declared property `owner` which represent a team / user group
which owns it. It is useful to have iit in bigger organization wth larger number of teams, topics, principals/applications, etc...

Kafkistry uses [UserOwnerVerifier](kafkistry-security/src/main/kotlin/com/infobip/kafkistry/webapp/security/auth/owners/UserOwnerVerifier.kt)
abstraction to check is particular User (logged-in person in UI) a member of particular user group (is user particular owner).

One example where Kafkistry uses it is when request to reset consumer group offsets is made. 
Kafkistry wants to check that person doing it is allowed to do so by indirectly checking is that user owner of Principal's 
declared owner, which has ACL to particular consumer group, is able to join that group. 
```
  [consumer-group] <- [ACL for READ GROUP] <- [Principal] <-(is owner)- [User]
```

### Static owner groups

Similarly to [static user list](#static-users-list), you can pass in list of which user is member of which owner group.

Options to pass in the owner groups.
- `OWNER_GROUPS`
   + one user format: `<owner/group>|<username1>,<username2>,...`
      * `owner/group` - owner name
      * `usernameN` - username who s member of owner group
   + multiple owner groups can be separated either by new line `\n` or by `;`
   + example with 2 users separated by `;`:
      - `OWNER_GROUPS=g1|u1,u2;g1|u1,u3`
- `OWNER_GROUPS_YAML` - value holding yaml
   + holds same information as raw `OWNER_GROUPS` but in yaml format
   + example:
```yaml
- owner: "my-owner-group-1"
  usernames:
   - "user1"
   - "user2"
- owner: "my-owner-group-2"
  usernames:
   - "user3"
# more owner-groups...
```
- `OWNER_GROUPS_YAML_FILE` - path to file holding yaml
   + same format as `OWNER_GROUPS_YAML`

**NOTE/TIP**: any change in owners list will require restart of Kafkistry

### Security setup for managing consumer groups

In larger organizations you may want to restrict who can manage which consumer group.

| Property                                    | Default | Description                                                                                                                                                                                                                                                                                |
|---------------------------------------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app.security.consumer-groups.only-admin`   | `false` | When `true` then only ADMIN users are able to delete consumer groups, reset offsets regardless of non-admin user actually being owner of particular consumer group                                                                                                                         |
| `ALLOW_ACCESS_TO_CONSUMER_GROUPS_NO_OWNERS` | `false` | In case when there are no ACL-s affecting particular consumer group, then Kafkistry can't resolve which principal and therefore which `owner` owns access to consumer group. When this property is `false` then non-admin users wont be permitted to make changes on such consumer groups. |

Implementation class for above listed properties is [ConsumerAlterPermissionAuthorizer](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/consumers/ConsumerAlterPermissionAuthorizer.kt)



## High Availability (HA) setup - multiple Kafkistry nodes

Kafkistry can run as multi-node cluster in order to achieve high-availability.

Kafkistry as tool for management and inspection for kafka is not critical part of whole system. 
If Kafkistry consumer lag metric is being used for monitoring, then it's useful to have HA because you might not want to 
unavailability of Kafkistry makes you blind for not knowing lag of all consumers.

High availability comes from having more than one instance/node of Kafkistry being deployed so that if one goes down you still 
have other to handle user requests / export lag.

In multi instance setup each Kafkistry operates very similarly as standalone instance except few things:
 - user sessions are being shared across instances so that if user is logged-in on one Kafkistry instance then subsequent 
   HTTP-request to another Kafkistry instance will work under the same `KRSESSIONID`
 - Kafkistry instances send event's to each other about changes so that if topic is being created by one Kafkistry instance, and
   subsequent UI HTTP request goes to another Kafkistry instance, the fact that event about creating will be sent helps to
   avoid situation of second Kafkistry not being aware of topic being created moments ago by first Kafkistry instance 

A typical setup would be to place HTTP proxy in front multiple Kafkistry nodes sto that proxy balances requests across all 
available Kafkistry nodes. Could be in a simple round-robin strategy, no need for sticky session on proxy because of 
session sharing between Kafkistry (KR) nodes.
```
                                    . . . . . . .      Hazelcast
                                    .  +----+   .  /--- cluster
                                 /---->| KR |   . /
                                /   .  +----+   ./
+---------+         +--------+ /    .           .
|  User   | request |  HTTP  |/     .  +----+   .
| browser |-------->|  proxy |-------->| KR |   .
+---------+         +--------+\     .  +----+   .
                               \    .           .
                                \   .  +----+   .
                                 \---->| KR |   .    
                                    .  +----+   .    
                                    . . . . . . .        
```

How are user sessions being shared and events sent between Kafkistry nodes?
 - answer: hazelcast in-memory-data-grid (IMDG)

### Hazelcast

Kafkistry uses [Hazelcast IMDG](https://docs.hazelcast.com/imdg/latest/index.html) 
for user's session sharing and sending-receiving events between Kafkistry instances.

In order to make it work Kafkistry nodes must somehow know about each other to connect together by forming hazelcast cluster.

Options to configure hazelcast:

| Property                        | Default       | Description                                                                                                                                             |
|---------------------------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `HAZELCAST_PORT`                | `5701`        | Port on which Kafkistry embedded hazelcast will listen on. Make sure to add it in port mapping if running inside docker.                                |
| `HAZELCAST_ADVERTIZED_IP`       | _none_        | This is an IP address which will be advertised, it's important that each Kafkistry node can reach each other Kafkistry node by this IP and port         | 
| `HAZELCAST_DISCOVERY_TYPE`      | `MULTICAST`   | Configure how should Kafkistry nodes discover each other. Available options: `NONE`, `CUSTOM_IMPLEMENTATION`, `LOCALHOST_IP`, `MULTICAST`, `STATIC_IPS` |
| `HAZELCAST_PUBLISH_ACK_TIMEOUT` | `6000` (6sec) | How much time should one Kafkistry instance wait for others acknowledging that they received and handled published event                                |

Discovery types:
 - `NONE` - don't join to hazelcast cluster
 - `MULTICAST` - let the hazelcast try to discover other Kafkistry nodes by sending multicast to network
 - `LOCALHOST_IP` - only discover nodes running on localhost (used mainly for tests)
 - `STATIC_IPS` - discover nodes from configured list of IP addresses
    + when using this option, then the `HAZELCAST_MEMBER_IPS` should be defined which will contain a list of IPs to look
 - `CUSTOM_IMPLEMENTATION` - an option which allows custom implementation to supply seed IPs at runtime
    + this option might be useful in situations where MULTICAST does not work because of various network configuration 
      blockers (such as firewalls, big network causing slow discovery,...), and you don't want to _hard-code_ `HAZELCAST_MEMBER_IPS`
    + To provide an implementation, implement interface [CustomDiscoveryIpsProvider](kafkistry-security/src/main/kotlin/com/infobip/kafkistry/webapp/hazelcast/HazelcastConfig.kt)
      * See more about [writing custom plugins](#writing-custom-plugins)



## Customizing topic wizard

### Partition count
Topic wizard proposes partition count based on expected message rate.
There is possibility to define thresholds for specific expected message rate onto desired partition count.

Options to default partition count for new topics:

| Property                      | Default                                      | Description                                                                                                                                                                    |
|-------------------------------|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `WIZARD_DEFAULT_PARTITIONS`   | `1`                                          | Default number of partitions to propose when rate is lower than lowest threshold defined in `WIZARD_PARTITION_THRESHOLDS`                                                      |
| `WIZARD_PARTITION_THRESHOLDS` | `{"default":{"100":10,"2000":30,"5000":60}}` | This is JSON representation of data class [ThresholdsConfig](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/wizard/TopicWizardConfigGenerator.kt) | 

### Concrete example

`WIZARD_DEFAULT_PARTITIONS` = `1` and `WIZARD_PARTITION_THRESHOLDS` =
```json
{
   "default": {
      "100": 10,
      "2000": 30,
      "5000": 60
   },
   "overrides": {
      "my-kafka-1": {
         "0": 2,
         "1000": 4
      },
      "my-kafka-3": {
         "200": 5
      }
   },
   "tagOverrides": {
      "some-tag": {
         "0": 20
      }
   }
}
```
Table shows what would be proposed partition count given some expected message rate for different clusters:

| Expected rate | my-kafka-1   | my-kafka-2    | my-kafka-3   | my-kafka-4 \[tag=some-tag]   |
|---------------|--------------|---------------|--------------|------------------------------|
| 10 msg/sec    | 1 partition  | 2 partitions  | 1 partition  | 20 partitions                |          
| 200 msg/sec   | 2 partitions | 10 partitions | 5 partitions | 20 partitions                |          
| 3000 msg/sec  | 4 partitions | 30 partitions | 5 partitions | 20 partitions                |          

### Topic name generator

Default implementation and UI directly asks user to enter desired name of a topic.

If specific topic naming convention is required then custom implementation of 
[TopicNameGenerator](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/wizard/TopicNameGenerator.kt)
can be exposed as spring `@Bean` alongside with custom UI form to enter custom attributes needed.

Options for customizing topic name UI inputs:

| Property                                    | Default                                                                                                                   | Description                                                                                                                 | Requirement                                              |
|---------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------|
| `app.topic-wizard.topic-name.template-name` | `defaultWizardTopicName` _([implementation](kafkistry-web/src/main/resources/ui/topics/defaultWizardTopicName.ftl))_      | UI component (html) for input attributes for topic name generator (default implementation only asks for `name`)             | Must be classpath resource located in `ui/topics/`       |
| `app.topic-wizard.topic-name.js-name`       | `defaultWizardTopicName` _([implementation](kafkistry-web/src/main/resources/ui/static/topic/defaultWizardTopicName.js))_ | UI component (js) for extracting needed custom attributes from UI to be used for `TopicNameGenerator`                       | Must be classpath resource located in `ui/static/topic/` |
| `app.topic-wizard.topic-name.name-label`    | `Check topic name`                                                                                                        | Not important for logic, but makes more sense to define something like `Generated topic name` when custom generator is used | _none_                                                   |



## Consuming/reading records limits

Kafkistry has utility to consume records from topic and display them in the UI.
This feature is mainly intended for developer's debugging content in topics.

Since the result of consuming is translated as single HTTP-request's response, there are few limits to setup to
prevent memory exhaustion.

| Property                  | Default         | Description                                                          |
|---------------------------|-----------------|----------------------------------------------------------------------|
| `CONSUE_ENABLED`          | `true`          | Enable/disable ability to consume records                            |
| `CONSUME_MAX_RECORDS`     | `5000`          | Maximum number of records to return in UI                            |
| `CONSUME_MAX_WAIT_MS`     | `120000` (2min) | Maximum wait time before responding to user that here are no records |
| `CONSUME_POOL_BATCH_SIZE` | `2000`          | Will be used for consumer `max.poll.records` property                |
| `CONSUME_POOL_INTERVAL`   | `1000` (1sec)   | Will be used as argument for `KafkaConsumer.pool(timeout)`           |

When reading from topic a new `KafkaConsumer` will be created which will consumer with randomly generated 
consumer group ID, and it will not perform commits while consuming.

### Record masking

Topic's records usually contain user sensitive data which shouldn't be visible even to developers.
This feature allows specification which fields in which topics on which kafka clusters are sensitive.
When topic is being consumed by Kafkistry, all sensitive values will be replaced with dummy `***MASKED***` value.

Rules for masking can be defined via following configuration properties:

| Property                                                                          | Default | Description                                                                                                     |
|-----------------------------------------------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------------|
| `app.masking.rules.<my-rule-name>.target.clusters.<...cluster filter options...>` | _all_   | From which clusters to apply masking of topic records. See [cluster filtering options](#cluster-filter-options) |
| `app.masking.rules.<my-rule-name>.target.topics.<...filter options...>`           | _all_   | From which topics to apply masking of topic records. See [filtering options](#filter-options)                   |
| `app.masking.rules.<my-rule-name>.value-json-paths`                               | _none_  | Comma-separated list of json path field in record's deserialized value to apply masking replacement             |
| `app.masking.rules.<my-rule-name>.key-json-paths`                                 | _none_  | Comma-separated list of json path field in record's deserialized key to apply masking replacement               |
| `app.masking.rules.<my-rule-name>.headers-json-paths.<header-name>`               | _none_  | Comma-separated list of json path field in record's deserialized header to apply masking replacement            |

Rules for masking can be obtained by supplying custom implementation of
[RecordTimestampExtractor](kafkistry-consume/src/main/kotlin/com/infobip/kafkistry/service/consume/masking/RecordMaskingRuleProvider.kt)
as spring bean.


## Oldest record age sampling/analysis

It is useful to know how old is the oldest record in topic partition.
This information gives insight what is the _"effective time retention"_ in cases when topic has 
low `retention.bytes` and high throughput, which might cause effective retention to be much 
less than `retention.ms`.

| Property                                                                   | Default | Description                                                                                                           |
|----------------------------------------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------|
| `OLDEST_RECORD_AGE_ENABLED`                                                | `true`  | Enable/disable sampling of oldest (lowest offset) record timestamp                                                    |
| `app.oldest-record-age.enabled-on.clusters.<...cluster filter options...>` | _all_   | Nested properties for which **clusters** to enable sampling. See [cluster filtering options](#cluster-filter-options) |
| `app.oldest-record-age.enabled-on.topics.<...filter options...>`           | _all_   | Nested properties for which **topics** to enable sampling. See [filtering options](#filter-options)                   |

Default implementation for extraction of timestamp from `ConsumerRecord` simply uses `ConsumerRecord.timestamp()` method.
This behaviour can be changed by supplying custom implementation of 
[RecordTimestampExtractor](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/oldestrecordage/RecordTimestampExtractor.kt)
as spring bean.

## Record structure analysis

This feature tries to detect structure of messages in topics.

Current implementation supports analysis only for `JSON` serialized messages.

It works by taking samples of messages periodically. Each message goes through analysis to extract structural metadata 
and store this metadata in time-windowed tree index.

Result of analysis in non-deterministic because of non-deterministic selection of records to be sampled.
However, analysis gives pretty good results because of assumption that all messages from one
topic should have same or similar structure.

Options to configure analyzer.

| Property                                                                                | Default                          | Description                                                                                                                                                    |
|-----------------------------------------------------------------------------------------|----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `RECORD_ANALYZER_ENABLED`                                                               | `true`                           |                                                                                                                                                                |
| `RECORD_ANALYZER_STORAGE_DIR`                                                           | `kafkistry/record-analyzer-data` | Path to directory where Kafkistry will persist analyzed data on disk                                                                                           |
| `RECORD_ANALYZER_TRIM_TIME_WINDOW`                                                      | `259200000` (3days)              | Width of time window. (If some field in JSON hadn't been seen for more than this time it will be removed from index)                                           |
| `RECORD_ANALYZER_CONCURRENCY`                                                           | `1`                              | How many threads should handle analysis                                                                                                                        |
| `RECORD_ANALYZER_MAX_QUEUE_SIZE`                                                        | `20000`                          | Actual sampling is performed by different thread than analysing thread. This is max size of in-memory queue for records being sampled and waiting for analysis |
| `RECORD_ANALYZER_TRIM_AND_DUMP_RATE`                                                    | `120000` (2min)                  | How often to evict old fields from tree structure and to persist whole index on disk                                                                           |
| `RECORD_ANALYZER_VALUE_SAMPLING_ENABLED`                                                | `true`                           | Should values of json field be analysed, `false` means that only value type is indexed                                                                         |
| `RECORD_ANALYZER_VALUES_MAX_CARDINALITY`                                                | `25`                             | Threshold for how many different values are considered to be high-cardinality                                                                                  |
| `app.record-analyzer.enabled-on.clusters.<...cluster filter options...>`                | _all_                            | From which clusters to perform analysis of topic records. See [cluster filtering options](#cluster-filter-options)                                             |
| `app.record-analyzer.enabled-on.topics.<...filter options...>`                          | _all_                            | From which topics to perform analysis of topic records. See [filtering options](#filter-options)                                                               |
| `app.record-analyzer.value-sampling.enabled`                                            | `true`                           | Weather or not to sample values of analyzer records.                                                                                                           |
| `app.record-analyzer.value-sampling.max-number-abs`                                     | `1000000`                        | In case of numeric field having magnitude bigger than this value will cause to mark it as too-big                                                              |
| `app.record-analyzer.value-sampling.max-string-length`                                  | `50`                             | In case of string field having length more than this value will cause to mark it as too-big                                                                    |
| `app.record-analyzer.value-sampling.max-cardinality`                                    | `25`                             | In case of sampling sees more different values, particular field will be marked as high-cardinality field                                                      |
| `app.record-analyzer.value-sampling.enabled-on.clusters.<...cluster filter options...>` | _all_                            | From which clusters to sample values of topic records. See [cluster filtering options](#cluster-filter-options)                                                |
| `app.record-analyzer.value-sampling.enabled-on.topics.<...filter options...>`           | _all_                            | From which topics to sample values of topic records. See [filtering options](#filter-options)                                                                  |
| `app.record-analyzer.value-sampling.included-fields`                                    | _none_                           | Comma-separated list of json path field, sample values only from json fields of this whitelist                                                                 |
| `app.record-analyzer.value-sampling.excluded-fields`                                    | _none_                           | Comma-separated list of json path field, do not sample values from json fields of this blacklist                                                               |



## Scraping broker's disk capacity

Having information about brokers disk size is important part of capacity planing for topic configuration 
(number of partitions, replication factor and retention).

Unfortunately, brokers themselves do not expose disk metrics (size/free) where message logs are persisting.

Kafkistry allows for alternative ways to obtain information about disks.

### Static hard-coded total capacity

This option for Kafkistry having information about disk capacity of brokers is _poor man solution_.
The way it works is to tell Kafkistry about disk capacity via Kafkistry configuration properties.
It is naive approach, but it may suffice because disk size is constant for most of the time.

Properties needed to set:
 - `app.kafka.metrics.static.enabled: true`
 - If all brokers of one kafka cluster **have** disks with same total capacity:
    + `app.kafka.metrics.static.clusters.<CLUSTER_IDENTIFIER>.total-default: <TOTAL_CAPACITY_BYTES>`
 - If all brokers of one kafka cluster **don't have** disks with same total capacity:
    + `app.kafka.metrics.static.clusters.<CLUSTER_IDENTIFIER>.broker-total.<BROKER_ID>: <TOTAL_CAPACITY_BYTES>`
 - Both options can be used at same time, in case if 4 of 6 brokers have same disk, and 2 of 6 have different
 - Example:
```yaml
app.kafka.metrics.static:
  enabled: true
  clusters:
    my-kafka-1:
      total-default: 322122547200   # 300GB
      broker-total:
        5: 289910292480     # 270GB - broker 5 does not have 300GB
        6: 289910292480     # 270GB - broker 6 does not have 300GB
    my-kafka-2:
      total-default: 1099511627776  # all brokers have 1TB
```

### Obtaining disk size and free space from Prometheus

This option queries prometheus server to get values for broker's disk capacity and/or disk free space.
This approach is suitable only if you already use prometheus and do have those metrics collected (e.g., by node_exporter).

Depending on organization of your metrics labels, there are two different ways for fetching metrics:
 - one request to Prometheus for each broker
 - one bulk request to Prometheus for all brokers of one cluster

Properties to configure:
 - `app.kafka.metrics.prometheus.enabled`
   + Is prometheus scraping enabled, need to be `true` in order to work
   + default: `false`
 - `app.kafka.metrics.prometheus.bulk`
   + Weather to use bulk mode, or one-by-one broker mode
   + default: `false`
 - `app.kafka.metrics.prometheus.prometheus-base-url`
   + Needed to generate url for making request to prometheus
   + Example: `https://my-prom.server`
 - `app.kafka.metrics.prometheus.time-offset`
   + Time offset from now into the past
   + Default: `60` (1min)
 - `app.kafka.metrics.prometheus.http-headers.<HTTP_HEADER>`
   + Add any additional http-header(s) to request Kafkistry will make towards Prometheus
   + It might be useful in situation if your Prometheus is sitting behind a proxy which requires some form of authentication header
   + Example: `app.kafka.metrics.prometheus.http-headers.X-AUTH-PROM-TOKEN: abcdef0123456789`
 - `app.kafka.metrics.prometheus.total-prom-query`
   + PromQL template for fetching **total** disk capacity
   + Example of query for metrics exported by node_exporter
     * single query: `node_filesystem_size{mountpoint='/my/used/mountpoint', instance=~'(?i){brokerHost}.*'}`
     * bulk query: `node_filesystem_size{mountpoint='/my/used/mountpoint', instance=~'(?i)({brokerHosts}).*'}`
   + **NOTE**: `{brokerHost}` is a template variable which will be injected into the query (it's broker's advertised hostname).
    `{brokerHosts}` is a template variable of all broker hostnames separated by `|` 
 - `app.kafka.metrics.prometheus.free-prom-query`
   + PromQL template for fetching **free** disk capacity.
   + Example of query for metrics exported by node_exporter
     * single query: `node_filesystem_free{mountpoint='/my/used/mountpoint', instance=~'(?i){brokerHost}.*'}`
     * bulk query: `node_filesystem_free{mountpoint='/my/used/mountpoint', instance=~'(?i)({brokerHosts}).*'}`
 - `app.kafka.metrics.prometheus.broker-label-name`
   + Needed if `bulk: true`
   + Name of label which holds broker host name (needed to match metric sample with specific broker host)
   + Example (corresponding to our PromQL example): `instance`
 - `app.kafka.metrics.prometheus.broker-label-host-extract-pattern`
   + Needed if `bulk: true`
   + Pattern to extract broker's host from label value (needed to match metric sample with specific broker host)
   + Default value: `(.*)`
   + Example (corresponding to our PromQL example):
     * Example with node exporter will likely have node exporter's endpoint inside `instance` label which will
       contain a port where node exporter exports metrics (for example, value might look like `my-broker-1:9100`).
       Here, we are interested in only in `my-broker-1` part, without port `9100`
     * example pattern: `^(.*):[0-9]+`
    
### Custom implementation for fetching broker disk metrics

In case when above listed mechanisms can't fulfill your needs, there is option to implement your
own mechanism for obtaining broker's total disk capacity and free disk space.

Implement interface [NodeDiskMetricsProvider](kafkistry-state-scraping/src/main/kotlin/com/infobip/kafkistry/kafkastate/brokerdisk/NodeDiskMetricsProvider.kt)
which could obtain needed metrics with your mechanism of choice.

See more about [writing custom plugins](#writing-custom-plugins)



## KStreams detection

Kafkistry supports simple detection of KStreams applications being deployed.
It works by:
 - finding consumer group using partition assignor `streams`
 - topics consumed by that consumer group are marked as _inputTopics_ of KStream app
 - topics having name `<consumerGroup/kStreamAppId>-KSTREAM-*` are marked as _internalTopics_ of KStream app

| Property                                                                   | Default | Description                                                                                    |
|----------------------------------------------------------------------------|---------|------------------------------------------------------------------------------------------------|
| `app.kstream.detection.enabled-on.clusters.<...cluster filter options...>` | _all_   | On which clusters to detect KStreams. See [cluster filtering options](#cluster-filter-options) |
| `app.kstream.detection.enabled-on.consumer-groups.<...filter options...>`  | _all_   | For which consumer groups to detect KStreams. See [filtering options](#filter-options)         |
| `app.kstream.detection.enabled-on.topics.<...filter options...>`           | _all_   | Which topics to include in KStreams detection. See [filtering options](#filter-options)        |



## Topic inspection

Kafkistry inspects various aspects of a topic on specific cluster.
Main aspects are existence of topic where it's expected to exist, does it have expected configuration, etc...

### Extra topic inspectors

Topic inspection can be enriched with custom implementations of
[TopicExternalInspector](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/TopicExternalInspector.kt).

All built-in [extra inspectors](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/inspectors) are enabled by default.
Particular inspectors can be enabled or disabled using properties:
- `app.topic-inspect.enabled-inspectors`
- `app.topic-inspect.disabled-inspectors`
  
where value is list of fully qualified class names to enable/disable.

Example:
```yaml
app.topic-inspection:
  enabled-inspectors:
    - com.infobip.kafkistry.service.topic.inspectors.TopicEmptyInspector
    - com.infobip.kafkistry.service.topic.inspectors.TopicUnderprovisionedRetentionInspector
```

#### Configurable built-in topic inspectors

- [TopicUnderprovisionedRetentionInspector](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/inspectors/TopicUnderprovisionedRetentionInspector.kt)
   - Check if topic has partitions such that configured `retention.bytes` is too low so that oldest messages are deleted before reaching `retention.ms`
   - When this happens it usually means traffic rate is high so that topic doesn't have time based retention of `retention.ms` as one might expect
   -
     | Property                                                                            | Default | Description                                                                                                                                  |
     |-------------------------------------------------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------|
     | `app.topic-inspection.underprovisioned-retention.min-oldness-ratio-to-retention-ms` | `0.8`   | Trigger this status when oldest record in partition is younger than 80% of topic's `retention.ms`                                            |
     | `app.topic-inspection.underprovisioned-retention.required-ratio-to-retention-bytes` | `0.8`   | Prevent this status from triggering unless partition is "loaded" with data; i.e. partition size is at least 80% of topic's `retention.bytes` |
   
- [TopicOverprovisionedRetentionInspector](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/inspectors/TopicOverprovisionedRetentionInspector.kt)
   - Check if topic has partitions such that configured `retention.bytes` is mush bigger than actual size of partition because messages are being deleted before by `retention.ms`
   - When this happens it usually means traffic rate is low
   -
     | Property                                                                           | Default             | Description                                                                                                                                                        |
     |------------------------------------------------------------------------------------|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
     | `app.topic-inspection.overprovisioned-retention.min-used-ratio-to-retention-bytes` | `0.2`               | Trigger this status when partition size is 20% or less than topic's `retention.bytes`                                                                              |
     | `app.topic-inspection.overprovisioned-retention.required-ratio-to-retention-ms`    | `0.8`               | Prevent this status from triggering unless messages are being deleted by time retention; i.e. oldest record being at old at least as 80% of topic's `retention.ms` |
     | `app.topic-inspection.overprovisioned-retention.ignore-below-retention-bytes`      | `10485760` _(10MB)_ | Don't trigger for small topics; i.e. having `retention.bytes` less than 10MB                                                                                       |
   


### Topic rule violations

Secondary aspects of topic inspection are various (and custom pluggable) implementations of [ValidationRule](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/validation/rules/ValidationRule.kt).
Any rule that raises violation will be shown as `CONFIG_RULE_VIOLATION`.

All built-in [validation rules](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/validation/rules) are enabled by default.
Particular rules can be enabled or disabled using properties:
 - `app.topic-validation.enabled-rules` 
 - `app.topic-validation.disabled-rules`

where value is list of fully qualified class names to enable/disable.

Example:
```yaml
app.topic-validation:
  disabled-rules:
    - com.infobip.kafkistry.service.validation.rules.MinInSyncReplicasTooBigRule
    - com.infobip.kafkistry.service.validation.rules.SizeRetentionEnoughToCoverRequiredRule
```

#### Configurable validation rules

- [SegmentSizeToRetentionBytesRatioRule](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/validation/rules/SegmentSizeToRetentionBytesRatioRule.kt)
  - Verifies that topic's segment size is not too big nor too small comparing to retention size
  -
    | Property                                                              | Default | Description                                                             |
    |-----------------------------------------------------------------------|---------|-------------------------------------------------------------------------|
    | `app.topic-validation.segment-size-rule.max-ratio-to-retention-bytes` | `0.5`   | Raises violation if topic's `segmment.bytes` > `0.5 * retention.bytes`  |
    | `app.topic-validation.segment-size-rule.min-ratio-to-retention-bytes` | `0.02`  | Raises violation if topic's `segmment.bytes` < `0.02 * retention.bytes` |

- [KStreamPartitionCountRule](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/topic/validation/rules/KStreamPartitionCountRule.kt)
  - In most cases, when using KStreams there is requirement that joining topics have the same partition count so called co-partitioning.
  - This rule exists to warn before proceeding to change partition count of one of topics involved in specific KStream application
  - This rule might raise false positive violation in cases when co-partitioning is not requirement. In such cases this rule can be disabled for particular topic, or whole rule can be disabled.
  -
    | Property                                                                                         | Default | Description                                                                                                                        |
    |--------------------------------------------------------------------------------------------------|---------|------------------------------------------------------------------------------------------------------------------------------------|
    | `app.topic-validation.kstream-partition-count.enabled-on.clusters.<...cluster filter options..>` | _all_   | On which clusters to check KStream's topics partition count for co-partition match, see [cluster options](#cluster-filter-options) |
    | `app.topic-validation.kstream-partition-count.enabled-on.topics.<...filter options...>`          | _all_   | For which topics to check KStream's topics partition count for co-partition match, see [options](#filter-options)                  |


## ACLs inspection

Kafkistry inspects the status of each ACL rule, for example is it OK, MISSING, UNKNOWN, etc. 

### ACLs conflicts

Beside that basic inspection, there is checking for ACL rules _conflict_.

Currently, there are two types of conflicts detection implemented:
 * **Consumer group** conflict
   + Triggered when there are at least two different principals which have **READ** permission on some `group.id` which would allow all of them joining to same consumer group at  the same time
 * **Transactional ID** conflict
   + Triggered when there are at least two different principals which have **WRITE** permission on some `transactional.id` which would allow all of them using same transactional id at the same time

Those checks can be disabled globally or per specific cluster:

| Property                                                                                         | Default | Description                                                                                                                                                  |
|--------------------------------------------------------------------------------------------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ACL_GROUP_CONFLICT_ENABLED`                                                                     | _true_  | Flag to globally enable/disable consumer group conflict checking.                                                                                            |
| `app.acl.conflict-checking.consumer-groups.enabled-on-clusters.<...cluster filter options...>`   | _all_   | Selective options to filter for which clusters to perform **consumer groups** ACL conflict checking. See [cluster filter options](#cluster-filter-options)   |
| `ACL_TRANSACTIONAL_ID_CONFLICT_ENABLED`                                                          | _true_  | Flag to globally enable/disable transactional id conflict checking.                                                                                          |
| `app.acl.conflict-checking.transactional-ids.enabled-on-clusters.<...cluster filter options...>` | _all_   | Selective options to filter for which clusters to perform **transactional ids** ACL conflict checking. See [cluster filter options](#cluster-filter-options) |


### ACLs DETACHED inspection

ACL rule is considered _"detached"_ when it references resource (topic/consumer group) which doesn't exist.
*DETACHED* status is warning type which it not problematic on its own but could indicate that corresponding ACL is 
no longer needed.

Configuration properties:

| Property                                      | Default | Description                                                                                                                                                                                                                                                                                                                                                                            |
|-----------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app.acl-inspect.detached.allow-global-check` | `false` | When `false` *detached* issue will be raised if ACL does not reference any resource (topic/group) looking on resources on same kafka cluster. When `true` then ACL on cluster _c1_ referencing resource _r1_ won't raise issue even though _r1_ is not found on _c1_ but is found on some other cluster (say _c2_), but it will still raise issue if _r1_ is not found on any cluster. |
| `app.acl-inspect.detached.ignore-any-user`    | `false` | When `true` inspection will ignore possible detach issue for principal `User:*`.                                                                                                                                                                                                                                                                                                       |


## Cluster inspection

There are inspectors on per cluster level looking for issues. Those can be enabled/disables/configured.

### Built-in cluster-level issue checkers

Existing checkers are enabled by default, but can be disabled with following property:
 - `app.clusters-inspect.excluded-checker-classes`
where value is list of fully qualified class names to disable/exclude from evaluation.

 - [ClusterDisbalanceIssuesChecker](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/cluster/inspect/ClusterDisbalanceIssuesChecker.kt)
   - It reports issues when there is uneven disk usage/replica count/leaders count on brokers within same cluster
   -
     | Property                                                                 | Default    | Description                                                                                                                                                                                                                                                                              |
     |--------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
     | `app.clusters-inspect.disbalance.disk-usage.enabled`                     | `true`     | Is disk usage disbalance checked.                                                                                                                                                                                                                                                        |
     | `app.clusters-inspect.disbalance.disk-usage.max-acceptable-percent`      | `20` (_%_) | Trigger the issue if difference between max disk usage in cluster comparing to min disk usage in cluster is more than *percent* of average disk usage.                                                                                                                                   |
     | `app.clusters-inspect.disbalance.disk-usage.min-usage-threshold-percent` | `10` (_%_) | Won't trigger the issue when disk usage on all brokers is less then given this threshold percentage. (to avoid noisy issue for low utilized clusters). This suppressing issue feature depends on Kafkistry's ability to get [disk capacity of brokers](#scraping-brokers-disk-capacity). |
     | `app.clusters-inspect.disbalance.replica-count.enabled`                  | `true`     | Is broker's replica count disbalance checked.                                                                                                                                                                                                                                            |
     | `app.clusters-inspect.disbalance.replica-count.max-acceptable-percent`   | `10` (_%_) | Trigger the issue if difference between max replica count broker comparing to min replica count in cluster is more than *percent* of average replica count per broker in cluster.                                                                                                        |
     | `app.clusters-inspect.disbalance.leader-count.enabled`                   | `true`     | Is broker's leader count disbalance checked.                                                                                                                                                                                                                                             |
     | `app.clusters-inspect.disbalance.leader-count.max-acceptable-percent`    | `10` (_%_) | Trigger the issue if difference between max leader count broker comparing to min leader count in cluster is more than *percent* of average leader count per broker in cluster.                                                                                                           |
 - [ClusterReplicationThrottleChecker](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/cluster/inspect/ClusterReplicationThrottleChecker.kt)
   - It reports issues when there is replication throttle being set
 - [DiskUsageIssuesChecker](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/cluster/inspect/DiskUsageIssuesChecker.kt)
   - It reports issues when disk usage is _"significant"_
   - It reports issue if all topic's replicas `retention.bytes` hosted on each broker add up to more than broker's disk capacity.
   - Depends on Kafkistry's ability to get [disk capacity of brokers](#scraping-brokers-disk-capacity)
   - Meaning what is meaning of _"significant"_ can be defined via following properties.
   -
     | Property                          | Default      | Description                                                                      |
     |-----------------------------------|--------------|----------------------------------------------------------------------------------|
     | `app.resources.thresholds.low`    | `65.0` (_%_) | Upper bound for usage percentage to be classified as *low*.                      |
     | `app.resources.thresholds.medium` | `90.0` (_%_) | Upper bound for usage percentage to be classified as *medium*. (above is *high*) |
 - [ClusterBrokerRackChecker](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/cluster/inspect/ClusterBrokerRackChecker.kt)
   - It reports issues when there is un-even number of brokers having same `broker-rack` defined. This check is there to ensure that brokers are evenly spread onto different racks/availability-zones.
   - By default, when all brokers have `broker-rack=null` or some `broker-rack=<CUSTOM_UNDEFINED_VALUE>` situation is then considered as OK.
   - 
     | Property                                         | Default        | Description                                                                                                                                                                                                                                                        |
     |--------------------------------------------------|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
     | `app.clusters-inspect.rack.undefined-rack-value` | `""` *(empty)* | Custom specific value that is considered as *"undefined"*                                                                                                                                                                                                          |
     | `app.clusters-inspect.rack.tolerate-undefined`   | `true`         | When `true` then issue will be raised if any of brokers has *undefined* `broker.rack`                                                                                                                                                                              |
     | `app.clusters-inspect.rack.tolerate-all-qual`    | `false`        | When `false` then issue will be raised if all brokers have same *non-undefined* `broker.rack`                                                                                                                                                                      |
     | `app.clusters-inspect.rack.strict-balance`       | `false`        | When `true` then strict balance is required, meaning that there is exact equal numbers of brokers with same `broker.rack`. Note, strict balance is not possible in situations where number of brokers is not divisible by number of different `broker.rack`-values |
   

### Custom cluster-level issue checkers

Custom checker can be implemented via interface [ClusterIssueChecker](kafkistry-service-logic/src/main/kotlin/com/infobip/kafkistry/service/cluster/inspect/ClusterIssueChecker.kt)
and being picked up by Spring Framework as bean. (see more on [Writing custom plugins](#writing-custom-plugins))


## SQL metadata querying - SQLite

Kafkistry has variety of different metadata about clusters, topics, consumer groups, etc... 
All this metadata (as well as some inspection statuses) are periodically stored into
embedded SQLite database.

Having ability to make SQL queries with arbitrary joins and filtering makes it very useful
tool for getting insights about anything that's available in all data. 
SQL is simply more expressive than many custom UI's.

You can check schema help and query examples in the UI.

SQL feature uses `SQLite` which requires local disk storage.

Properties to configure:

| Property                       | Default            | Description                                                                                                                        |
|--------------------------------|--------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `SQL_ENABLED`                  | `true`             |                                                                                                                                    |
| `SQLITE_DB_DIR`                | `kafkistry/sqlite` | Path to directory where SQLite should store its DB. Can be absolute path or relative to working dir                                |
| `SQL_AUTO_CREATE_DIR`          | `true`             | If `true` and specified directory `SQLITE_DB_DIR` doesn't exist, Kafkistry will attempt to create it before using                  |
| `SQL_CLICKHOUSE_OPEN_SECURITY` | `false`            | Is endpoint for accessing SQL rest API (ClickhouseDB-like API) accessible anonymously (without session and without authentication) |

### ClickhouseDB-like REST API

Kafkistry is capable of exposing its SQL DB access through API similar to ClickhouseDB's API.

Http endpoint which exposes it is:
```
HTTP GET $HTTP_ROOT_PATH/api/sql/click-house
```
For example: (depending on `HTTP_ROOT_PATH`, see [web configuration](#customizing-web-app)):
```
HTTP GET /api/sql/click-house
HTTP GET /kafkistry/api/sql/click-house
HTTP GET /your-http-root-path/api/sql/click-house
```

This endpoint serves as integration adapter for other tools such as Grafana or Redash.
 - **Grafana**
   + It is possible to query Kafkistry SQL directly from Grafana
   + setup steps: 
     * install plugin `ClickHouse by Vertamedia` to your Grafana
     * add new data source in Grafana for Clickhouse plugin
     * URL: `http://<your-kr-host>:8080/<your-http-root-path>/api/sql/click-house`
     * Whitelisted cookies: `KRSESSIONID`
     * Auth: `Basic auth` (Enter username/password to Basic Auth Details)
        - consider adding dedicated user for Grafana with role `READ_SERVICE` into 
          [static users list](#static-users-list)
 - **Redash**
   + It is possible to query Kafkistry's SQL directly from Redash
   + Setup is almost the same as for Grafana except that Redash doesn't offer to whitelist session cookie,
     for that reason, it's needed to set `SQL_CLICKHOUSE_OPEN_SECURITY: true`
     * add `ClickHouse` data source
     * Url: `http://<your-kr-host>:8080/<your-http-root-path>/api/sql/click-house`
     * Username and password can be blank when Kafkistry is deployed with `SQL_CLICKHOUSE_OPEN_SECURITY: true`
     * Database: `kr`



## Autopilot

Autopilot is functionality that Kafkistry performs some actions on Kafka clusters on its own without being externally
triggered by user through UI or API call.

There are various aut actions implemented, for example:
 - `CreateMissingTopicAction` - will be triggered immediately when autopilot detects that topic should exist but it doesn't exist on kafka
 - `DeleteUnwantdACLAction` - will be triggered immediately when autopilot detects that some existing ACL rule shouldn't exist
 - _..._

### WARNING / disclaimer

Autopilot is _beta_ feature, and it's recommended to be **disabled**. Reason is that we need to gain confidence 
in correctness of action being blocked checkers. Even though, thr are multiple checkers in place there is still possibility
that something is overlooked and that autopilot will perform some action even though it should wait. 
(for example: new topic shouldn't be created if some kafka broker is in maintenance)

If you want to **enable** autopilot, it's recommended to do so only if your state on cluster exactly matches whatever is
defined in Kafkistry's repository. Otherwise, autopilot might start to create/delete/alter many things.

### Configuration options

| Property                                                                         | Default                                     | Description                                                                                                                                                                                                                                                                                                                                           |
|----------------------------------------------------------------------------------|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AUTOPILOT_ENABLED`                                                              | `true`                                      | Completely enable/disable creation of autopilot components. When disabled, no periodic checking for actions will occur.                                                                                                                                                                                                                               |
| `AUTOPILOT_ACTIONS_ENABLED`                                                      | `false`                                     | Enable/disable all autopilot actions. When `false` no actions will be executed, when `true` other filters may apply.                                                                                                                                                                                                                                  |
| `app.autopilot.actions.action-type.enabled-classes`                              | _all_                                       | List of **enabled** implementations of [AutopilotAction](kafkistry-autopilot/src/main/kotlin/com/infobip/kafkistry/autopilot/binding/model.kt) class names. When _empty_, any action is enabled.                                                                                                                                                      |
| `app.autopilot.actions.action-type.disabled-classes`                             | _none_                                      | List of **disabled** implementations of [AutopilotAction](kafkistry-autopilot/src/main/kotlin/com/infobip/kafkistry/autopilot/binding/model.kt) class names. When _empty_, any action is enabled.                                                                                                                                                     |
| `app.autopilot.actions.target-type.enabled-values`                               | _all_                                       | List of **enabled** target types for particular action. Known target types are: [`TOPIC`, `ACL`]. When _empty_, then any target type is enabled.                                                                                                                                                                                                      |
| `app.autopilot.actions.target-type.disabled-values`                              | _none_                                      | List of **disabled** target types for particular action. Known target types are: [`TOPIC`, `ACL`]. When _empty_, then any target type is enabled.                                                                                                                                                                                                     |
| `app.autopilot.actions.binding-type.enabled-classes`                             | _all_                                       | List of **enabled** implementations of [AutopilotBinding](kafkistry-autopilot/src/main/kotlin/com/infobip/kafkistry/autopilot/binding/AutopilotBinding.kt) class names. When _empty_, actions coming from any binding are enabled.                                                                                                                    |
| `app.autopilot.actions.binding-type.disabled-classes`                            | _none_                                      | List of **disabled** implementations of [AutopilotBinding](kafkistry-autopilot/src/main/kotlin/com/infobip/kafkistry/autopilot/binding/AutopilotBinding.kt) class names. When _empty_, actions coming from any binding are enabled.                                                                                                                   |
| `app.autopilot.actions.cluster.<...filter options...>`                           | _all_                                       | Actions targeting which clusters to enable. See [filtering options](#filter-options)                                                                                                                                                                                                                                                                  |
| `app.autopilot.actions.attribute.<...action attribute...>.enabled-values`        | _all_                                       | List of **enabled** attribute values for actions that have attribute named `<...action attribute>`.                                                                                                                                                                                                                                                   |
| `app.autopilot.actions.attribute.<...action attribute...>.disabled-values`       | _all_                                       | List of **disabled** attribute values for actions that have attribute named `<...action attribute>`.                                                                                                                                                                                                                                                  |
| `AUTOPILOT_STORAGE_DIR`                                                          | `kafkistry/autopilot`                       | Path to directory where Kafkistry will store information about Autopilot actions and their outcomes.                                                                                                                                                                                                                                                  |
| `app.autopilot.repository.limits.max-count`                                      | `1000`                                      | Maximal number of different action executions to keep in storage.                                                                                                                                                                                                                                                                                     |
| `app.autopilot.repository.limits.retention-ms`                                   | `604800000` (7 days)                        | Delete older action outcomes than specified retention.                                                                                                                                                                                                                                                                                                |
| `app.autopilot.repository.limits.max-per-action`                                 | `50`                                        | Maximal number of stored state changes per each different action.                                                                                                                                                                                                                                                                                     |
| `app.autopilot.cycle.repeat-delay-ms`                                            | `10000` _(10 sec)_                          | How often to execute one cycle/round of autopilots discovery/checking/execution of actions.                                                                                                                                                                                                                                                           |
| `app.autopilot.cycle.after-startup-delay-ms`                                     | `60000` _(1 min)_                           | How long after application startup should autopilot start periodic executions.                                                                                                                                                                                                                                                                        |
| `app.autopilot.attempt-delay-ms`                                                 | `60000` _(1 min)_                           | Once action is tried to execute, how long to wait for re-attempt in case of failure.                                                                                                                                                                                                                                                                  |
| `app.autopilot.pending-delay-ms`                                                 | `10000` _(10 sec)_                          | Once action is discovered and without any blockers, how long to wait for execution. (This is workaround for issue that kafka node can return empty list of Topics/ACLs/Quotas shortly after restart, causing erroneous actions. Value should be `>=` [CLUSTER_POOL_INTERVAL](#pooling-metadata-from-clusters))                                        |
| `app.autopilot.cluster-requirements.stable-for-last-ms`                          | `600000` _(10 min)_                         | In order for action to be considered for execution, require cluster scraping to be constantly stable for _this_ period of time.                                                                                                                                                                                                                       |
| `app.autopilot.cluster-requirements.used-state-providers.<...filter options...>` | _all except: disc metrics, record sampling_ | Which kafka state providers to use for indication if cluster is stable or not. See available names as implementations of [AbstractKafkaStateProvider](kafkistry-state-scraping/src/main/kotlin/com/infobip/kafkistry/kafkastate/AbstractKafkaStateProvider.kt), to change defaults/include/exclude specific, see [filtering options](#filter-options) |

### Writing custom actions

Custom actions can be implemented via interface [AutopilotBinding](kafkistry-autopilot/src/main/kotlin/com/infobip/kafkistry/autopilot/binding/AutopilotBinding.kt).
Such implementation needs to be exposed as Spring bean to b picked up by Autopilot, and it will be handled in the same way as built-in implementations.
(see more on [Writing custom plugins](#writing-custom-plugins))




## Slack integration - auditing

It is possible to set up Kafkistry to send notification's to `slack` channel on each update in Kafkistry
repository or on some action on kafka cluster.

Properties to set:

| Property                  | Default | Description                                                                                                                                                                            |
|---------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SLACK_ENABLED`           | `false` |                                                                                                                                                                                        |
| `SLACK_CHANNEL_ID`        | _none_  | ID of slack channel to send notifications into                                                                                                                                         |
| `SLACK_SECRET_TOKEN`      | _none_  | Secret token to allow Kafkistry to authenticate on Slack's API, you'll need create slack bot application for your Slack workspace                                                      | 
| `SLACK_PROXY_HOST`        | _none_  | Optional property. Use it if your Kafkistry deployment does not have access to public internet and must go through SOCKS proxy to be able to access Slack' API                         |
| `SLACK_PROXY_PORT`        | _none_  | Optional property. _read description for SLACK_PROXY_HOST_                                                                                                                             |
| `SLACK_LINK_BASE_HOST`    | _none_  | User accessible base url for Kafkistry UI. Needed to generate message URL(s) properly. Example value: `https://my-kafka-proxy.local`                                                   |
| `SLACK_ENVIRONMENT_NAME`  | `n/a`   | Optional property. Useful if you have different Kafkistry(s) deployed in different environments and when message appears in slack channel to be abe to distinguish where it came from. |
| `SLACK_INCLUDE_AUTOPILOT` | `true`  | Optional property. Useful if you wish to exclude autopilot generated audit events in slack, then set this to `false`.                                                                  |



## JIRA integration

Kafkistry supports recognizing `JIRA`'s issue pattern (ABC-123) and generating links in UI pointing directly
to jira that you use.

This is more UI sugar than real integration where jira would be aware of Kafkistry.

| Property        | Example                         |
|-----------------|---------------------------------|
| `JIRA_BASE_URL` | `https://your-jira.com/browse/` |

When having JIRA_BASE_URL, Kafkistry UI will inject links to JIRA
```html
<a target='_blank' href="https://your-jira.com/browse/ABC-123">
    ABC-123
</a>
```



## Miscellaneous UI settings

| Property                  | Default                                                                                        | Description                                                                                                                                                |
|---------------------------|------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CUSTOM_JS_SCRIPTS_CSV`   | _empty_                                                                                        | Comma separated list of script files to be included into each UI view page (html)                                                                          |
| `app.ui.caching`          | `true`                                                                                         | Enables caching of static UI resources to speed up pages load, useful to set to `false` for development purposes.                                          |
| `app.ui.image.dir-path`   | `default`                                                                                      | Classpath directory which contain image files. Must be on classpath located in `ui/static/img/`                                                            |
| `app.ui.image.icon`       | `icon.png` _([resource](kafkistry-web/src/main/resources/ui/static/img/default/icon.png))_     | Browser tab icon image filename to be used in UI. Must be located in `ui/static/img/${app.ui.image.dir-path}/`                                             |
| `app.ui.image.logo`       | `logo.png` _([resource](kafkistry-web/src/main/resources/ui/static/img/default/logo.png))_     | Logo image filename to be used in UI. Must be located in `ui/static/img/${app.ui.image.dir-path}/`                                                         |
| `app.ui.image.banner`     | `banner.png` _([resource](kafkistry-web/src/main/resources/ui/static/img/default/banner.png))_ | Homepage banner image filename to be used in UI. Must be located in `ui/static/img/${app.ui.image.dir-path}/`                                              |
| `app.hostname.value`      | _(empty/none)_                                                                                 | Name of hostname to be shown in UI as `Served by: <hostname>`                                                                                              | 
| `app.hostname.property`   | _(empty/none)_                                                                                 | Name of system property or environment variable to resolve hostname from, to be shown in UI as `Served by: <hostname>`                                     | 
| `FORCE_TAG_FOR_PRESENCE`  | _false_                                                                                        | When set to `true` then only **Cluster Tag** presence option for topics/acls/quotas will be enabled while **All**/**Only on**/**Not on** will be disabled. | 



## Logging

Kafkistry uses _logback_ as logging framework.
Various appenders are defined conditionally by `LOGGING_TARGETS` _(environment variable)_ or `logging.targets` _(config property)_

Here are examples of defined `LOGGING_TARGETS` and corresponding outcome

| Example value           | Console output | File(s)   | Logstash  |
|-------------------------|----------------|-----------|-----------|
| _undefined_             | `ENABLED`      | _n/a_     | _n/a_     |
| `""` _(empty)_          | _n/a_          | _n/a_     | _n/a_     |
| `console`               | `ENABLED`      | _n/a_     | _n/a_     |
| `file`                  | _n/a_          | `ENABLED` | _n/a_     |
| `logstash`              | _n/a_          | _n/a_     | `ENABLED` |
| `file,logstash`         | _n/a_          | `ENABLED` | `ENABLED` |
| `file,console`          | `ENABLED`      | `ENABLED` | _n/a_     |
| `console,file,logstash` | `ENABLED`      | `ENABLED` | `ENABLED` |

### Logging to Console output

By default, Kafkistry will output all logging to console output (**stdout**). Unless, specified differently by
`LOGGING_TARGETS` or `logging.targets`

### Logging to files

Logging to files can be enabled if `file` is defined in:
- `LOGGING_TARGETS` (or `logging.targets`) env/property containing `file`

Each file logging appender is configured to roll maximum of `10` last files, each having `6000kB` max.

### Logging to Logstash

If environment variable `LOGSTASH_HOST` is present and `LOGGING_TARGETS` (or `logging.targets`) env/property contains `logstash`,
Kafkistry will send logging to it.

Properties to configure for logstash appender:

| Property            | Default     | Description                                                                                                         |
|---------------------|-------------|---------------------------------------------------------------------------------------------------------------------|
| `LOGSTASH_HOST`     | _n/a_       | **REQUIRED**: URL to logstash host where appender should send logging requests.                                     | 
| `LOGSTASH_FACILITY` | `kafkistry` | Which facility to declare with each logging message.                                                                | 
| `LOGSTASH_LEVEL`    | `DEBUG`     | Max logging level to be sent to logstash. Available levels: `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`, `ALL` | 



## Writing custom plugins

Kafkistry is JVM-based application written in [Kotlin](https://kotlinlang.org/) 
and using [Spring framework](https://spring.io/) for backend, 
and [jQuery](https://jquery.com/) with [Bootstrap](https://getbootstrap.com/) on frontend.

You might need to extend Kafkistry with custom functionality/logic. 
This section describes how to write custom logic on top of Kafkistry core implementation.

**NOTE**: following sections will describe one of possible ways to do it.

### Set up your project

Kafkistry uses [Maven](https://maven.apache.org/) for dependency management. 

Simplest way to start with minimal dependency issues is to inherit Kafkistry parent pom.
Example of your `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
   <modelVersion>4.0.0</modelVersion>

   <parent>
      <groupId>com.infobip.kafkistry</groupId>
      <artifactId>kafkistry-parent</artifactId>
      <version>{newest-release-version}</version>
   </parent>

   <groupId>org.mycompany</groupId>
   <artifactId>my-company-kafkistry</artifactId>
   <version>1.0-SNAPSHOT</version>

   <properties>
      <docker.skip-build>false</docker.skip-build>
      <docker.to-image>mycompany-kafkistry:${project.version}</docker.to-image>
      <docker.main-class>org.mycompany.kafkistry.MyCompanyKafkistryKt</docker.main-class>
   </properties>

   <dependencies>
      <dependency>
         <groupId>com.infobip.kafkistry</groupId>
         <artifactId>kafkistry-app</artifactId>
         <version>${parent.version}</version>
      </dependency>
      <dependency>
         <groupId>com.infobip.kafkistry</groupId>
         <artifactId>kafkistry-app</artifactId>
         <version>${parent.version}</version>
         <type>test-jar</type>
         <scope>test</scope>
      </dependency>
      <!-- might want to include spring-test and etc...-->
   </dependencies>
</project>
```

Then write your main class as follows:

```kotlin
package org.mycompany.kafkistry

import com.infobip.kafkistry.Kafkistry
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@Import(Kafkistry::class)
class MyCompanyKafkistry

fun main(args: Array<String>) {
   runApplication<MyCompanyKafkistry>(*args) {
      setAdditionalProfiles("defaults")
   }
}
```

From there you can star writing custom implementations of your components, 
just make sure that any of your custom bean is picked up by spring framework 
(i.e. annotated with `@Component`, `@Bean` function inside `@Configuration`, `@Contoller`, etc...)

**NOTE**: given example is written in Kotlin, but you can write it in Java or even Scala.
