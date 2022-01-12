# Contributing to Kafkistry

## Ask questions

Questions about usage, potential bugs/defects, how it works, future plans, unclear/missing documentation, etc... 
are welcome.

Feel free to ask questions:
- on [stackoverflow](http://stackoverflow.com) with tag `kafkistry`
- by opening an issue 

Do research if same/similar issue was already asked/addressed before.

Maintainers of project will try to respond as soon as convenient to do so.

## Report issues 

Found issue/defect in kafkistry's behaviour?

Report it by creating an issue.
Issue should contain following information:
- steps to reproduce the issue (if reproducible)
- what was the expected behaviour
- if possible, any potentially relevant logging output happened prior/during/after the issue happened
- environment being run on (OS/JVM version/Kafkistry version)

## Request features

Like Kafkistry so far, but would like to have _feature-xyz_?

Submit a feature request by creating an issue.
Feature request should contain following information:
 - motivation/use-case behind request
 - wanted/needed level:
   - would be nice to have
   - there is alternative but it's painful/time-consuming
   - absence of feature is complete blocker

Maintainers will triage request and respond with:
 - acceptance to add it to project roadmap
 - rejection with explanatory reason
 - viable alternative/workaround

## Submit a PR

No need for creating issue, but please reference it if relevant issue already exist.

Description of PR should contain same information as [feature request](#request-features)

General guidelines:
 - try to avoid introducing breaking changes
 - follow reasonable naming and existing component's layout in project
 - backend implementations should be written in kotlin and code should be formatted with
 - introducing dependency must not have licence which is stricter than Apache licence 2.0 

After acceptance, your work might be modified by maintainers prior to merging.