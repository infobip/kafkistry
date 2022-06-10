![](kafkistry-web/src/main/resources/ui/static/img/default/banner.png)

# Kafkistry

Registry service for [Apache Kafka](https://kafka.apache.org/) which keeps track of **topics**, **consumer-groups**, **acls**, **quotas** and their configuration/state/metadata across multiple **kafka-clusters**.

It allows performing administrative operations such as inspecting/creating/deleting/re-configuring topics/consumer-groups.

It also provides various analytical tools such as: SQL querying metadata; viewing topic records; 
analyzing json record structures; re-balancing cluster tools; etc.

### When would Kafkistry might help you?
 - dealing with multiple kafka clusters?
 - dealing with many topics / consumer groups / consumers / producers
 - spent too much time correctly writing `kafka-*.sh` commands?
 - manual calculation when doing resource planing prior/after creating topics?
 - need easy/fast way to finding specific messages in topics when troubleshooting issues?
 - having hard time attempting to balance cluster(s) due to complicated analysis of assignments of many topics at once?
 - want to have central exposure of consumer lag metric in contrast to every consumer application exporting it on its own
 - don't want everybody to create/delete/re-configure topics however/whenever they want, 
   but do want a way for anybody to be able to express whet they need (without writing elaborate JIRA tasks)


## Documentation
See complete [documentation](DOCUMENTATION.md) for configurable properties and plugins


## Quick start with docker

To run Kafkistry locally with minimal setup:
```bash
docker run -p 8080:8080 --hostname "kr.local" infobip/kafkistry:latest
    --USERS_PASSWORDS="foo|bar|Admy|Adminsky|admin@kafkistry.local|ADMIN|" 
    --OWNER_GROUPS="Test_Group|foo" 
    --GIT_COMMIT_TO_MASTER_BY_DEFAULT=true  
```
Once container starts open [http://localhost:8080](http://localhost:8080) and login with username: `foo` password: `bar`.

From there you can add kafka cluster to be tracked and check features.


## Build (without tests and GPG signing)
```
mvn clean compile package -DskipTests -Dgpg-sign-skip=true
```

## Test
```
mvn test integration-test -DenabledIntegrationTests=all
```

## What kafkistry does?

Kafkistry is an administrative tool for managing multiple kafka clusters.

Basic idea is to have a repository (for clusters, topics, acls...) and repository represents source of truth 
for "_wanted/expected_" configuration of entities kafkistry manages.

Kafkistry will periodically scrape metadata from registered kafka clusters and compare it against relevant metadata 
from its repository. Any mismatch will show in the UI with available resolution actions. 

For example:
 - missing topic on kafka cluster can be resolved by creating a topic with specified configuration or removing a topic from repository
 - wrong topic's config can be resolved by either altering topic's config on kafka or updating expected configuration in repository
 - unexpected existing ACLs on kafka can be either imported into repository or deleted from kafka
 - ...

Kafkistry is monolithic service which:
 - connects to kafka clusters
 - read/writes data from/to git repository (if configured)
 - servers web UI 


## Demo screenshots
See some [demo screenshots](demo-screenshoots/preview-demo.md) preview.
