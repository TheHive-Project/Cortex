# Change Log

## [2.1.3](https://github.com/TheHive-Project/Cortex/tree/2.1.3)

[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/2.1.2...2.1.3)

**Implemented enhancements:**

- Add configuration for drone continuous integration [\#156](https://github.com/TheHive-Project/Cortex/issues/156)
- Add PAP property to jobs list [\#146](https://github.com/TheHive-Project/Cortex/issues/146)

**Fixed bugs:**

- Wrong checks of role when an user is created [\#158](https://github.com/TheHive-Project/Cortex/issues/158)
- Unable to disable invalid responders [\#157](https://github.com/TheHive-Project/Cortex/issues/157)
- PAP field is ignored from job modal [\#152](https://github.com/TheHive-Project/Cortex/issues/152)
- SinkDB analyzer could not find DIG in the Cortex docker image [\#147](https://github.com/TheHive-Project/Cortex/issues/147)
- GUI Search Function is broken [\#145](https://github.com/TheHive-Project/Cortex/issues/145)

**Closed issues:**

- Systemd: cortex.service: Failed with result 'exit-code'.  [\#155](https://github.com/TheHive-Project/Cortex/issues/155)
- conf/logback.xml: Rotate logs [\#62](https://github.com/TheHive-Project/Cortex/issues/62)

## [2.1.2](https://github.com/TheHive-Project/Cortex/tree/2.1.2) (2018-10-12)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/2.1.1...2.1.2)

**Fixed bugs:**

- findSimilarJob function broken [\#144](https://github.com/TheHive-Project/Cortex/issues/144)

## [2.1.1](https://github.com/TheHive-Project/Cortex/tree/2.1.1) (2018-10-09)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/2.1.0...2.1.1)

**Implemented enhancements:**

- Change Debian dependencies [\#141](https://github.com/TheHive-Project/Cortex/issues/141)
- Allow Cortex to use a custom root context [\#140](https://github.com/TheHive-Project/Cortex/issues/140)
- Publish stable versions in beta package channels [\#138](https://github.com/TheHive-Project/Cortex/issues/138)

**Fixed bugs:**

- Fix Cache column in analyzers admin page [\#139](https://github.com/TheHive-Project/Cortex/issues/139)
- RPM update replace configuration file [\#137](https://github.com/TheHive-Project/Cortex/issues/137)
- Console output should not be logged in syslog [\#136](https://github.com/TheHive-Project/Cortex/issues/136)

## [2.1.0](https://github.com/TheHive-Project/Cortex/tree/2.1.0) (2018-09-25)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/2.1.0-RC1...2.1.0)

**Implemented enhancements:**

- Show PAP value in the Org \> Analyzers screen [\#124](https://github.com/TheHive-Project/Cortex/issues/124)
- Display cache configuration in analyzer admin page [\#123](https://github.com/TheHive-Project/Cortex/issues/123)

**Fixed bugs:**

- MISP API fails [\#109](https://github.com/TheHive-Project/Cortex/issues/109)
- File\_Info issue [\#53](https://github.com/TheHive-Project/Cortex/issues/53)
- Temporary files are not removed at the end of job [\#129](https://github.com/TheHive-Project/Cortex/issues/129)
- MISP fails to run analyzers [\#128](https://github.com/TheHive-Project/Cortex/issues/128)

**Merged pull requests:**

- Update resolvers in build.sbt to contain Maven as a dependency [\#130](https://github.com/TheHive-Project/Cortex/pull/130) ([adl1995](https://github.com/adl1995))

## [2.1.0-RC1](https://github.com/TheHive-Project/Cortex/tree/2.1.0-RC1) (2018-07-31)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/2.0.4...2.1.0-RC1)

**Implemented enhancements:**

- New TheHive-Project repository [\#112](https://github.com/TheHive-Project/Cortex/issues/112)

**Fixed bugs:**

- Analyzer Configuration Only Showing Global Configuration [\#104](https://github.com/TheHive-Project/Cortex/issues/104)
- First analyze of a "file" always fail, must re-run the analyze a second time [\#117](https://github.com/TheHive-Project/Cortex/issues/117)
- Analyzers filter in Jobs History view is limited to 25 analyzers [\#116](https://github.com/TheHive-Project/Cortex/issues/116)
- Fix redirection from Migration page to login on 401 error [\#114](https://github.com/TheHive-Project/Cortex/issues/114)

**Closed issues:**

- Automatic observables extraction from analysis reports. [\#111](https://github.com/TheHive-Project/Cortex/issues/111)
- ImportError: No module named 'cortexutils' on V2.0.4 [\#102](https://github.com/TheHive-Project/Cortex/issues/102)
- Error occur from thehive project request to cortex project [\#101](https://github.com/TheHive-Project/Cortex/issues/101)
- Analyzers disappear after deactivation and can not get enabled [\#98](https://github.com/TheHive-Project/Cortex/issues/98)
- Application.conf doesn't have Yeti config nor allows for API Auth [\#54](https://github.com/TheHive-Project/Cortex/issues/54)
- endless loop of cortex analyser call [\#36](https://github.com/TheHive-Project/Cortex/issues/36)
- Automated response via Cortex [\#110](https://github.com/TheHive-Project/Cortex/issues/110)
- Consider providing checksums for the release files [\#105](https://github.com/TheHive-Project/Cortex/issues/105)
- PAP as an analyzer restriction [\#65](https://github.com/TheHive-Project/Cortex/issues/65)

**Merged pull requests:**

- Update GitHub path [\#100](https://github.com/TheHive-Project/Cortex/pull/100) ([saadkadhi](https://github.com/saadkadhi))

## [2.0.4](https://github.com/TheHive-Project/Cortex/tree/2.0.4) (2018-04-13)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/2.0.3...2.0.4)

**Implemented enhancements:**

- Let a Read/Analyze User Display/Change their API Key [\#89](https://github.com/TheHive-Project/Cortex/issues/89)

**Fixed bugs:**

- Strictly filter the list of analyzers in the run dialog [\#95](https://github.com/TheHive-Project/Cortex/issues/95)
- Updating users by orgAdmin users fails silently [\#94](https://github.com/TheHive-Project/Cortex/issues/94)
- Fix analyzer configurations icons [\#93](https://github.com/TheHive-Project/Cortex/issues/93)
- Wrong page redirection [\#92](https://github.com/TheHive-Project/Cortex/issues/92)
- Sort analyzers list by name [\#91](https://github.com/TheHive-Project/Cortex/issues/91)
- Cortex 2.0.3 docker container having cortex analyzer errors [\#90](https://github.com/TheHive-Project/Cortex/issues/90)
- Install python3 requirements for analyzers in public docker image [\#58](https://github.com/TheHive-Project/Cortex/issues/58)

**Closed issues:**

- Insufficient Rights To Perform This Action [\#87](https://github.com/TheHive-Project/Cortex/issues/87)

## [2.0.3](https://github.com/TheHive-Project/Cortex/tree/2.0.3) (2018-04-09)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/2.0.2...2.0.3)

**Implemented enhancements:**

- Allow arbitrary parameters for a job [\#86](https://github.com/TheHive-Project/Cortex/issues/86)
- Change of global config for proxy is not reflected in analyzer's configurations [\#81](https://github.com/TheHive-Project/Cortex/issues/81)

**Fixed bugs:**

- Refresh Analyzers button not working [\#83](https://github.com/TheHive-Project/Cortex/issues/83)
- Version Upgrade of Analyzer makes all Analyzers invisible for TheHive \(Cortex2\) [\#75](https://github.com/TheHive-Project/Cortex/issues/75)

**Closed issues:**

- Allow specifying a cache period per analyzer [\#85](https://github.com/TheHive-Project/Cortex/issues/85)
- Display existing analyzers with invalid definition [\#82](https://github.com/TheHive-Project/Cortex/issues/82)
- Allow configuring auto artifacts extraction per analyzer [\#80](https://github.com/TheHive-Project/Cortex/issues/80)

## [2.0.2](https://github.com/TheHive-Project/Cortex/tree/2.0.2) (2018-04-04)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/2.0.1...2.0.2)

**Fixed bugs:**

- Coretxutils and TypeError: argument of type 'bool' is not iterable [\#73](https://github.com/TheHive-Project/Cortex/issues/73)
- Silently failure when ElasticSearch is unreachable [\#76](https://github.com/TheHive-Project/Cortex/issues/76)
- Unable to disable analyzers [\#72](https://github.com/TheHive-Project/Cortex/issues/72)
- Cortex 2 is not passing proxy variable to analyzers [\#71](https://github.com/TheHive-Project/Cortex/issues/71)
- Session collision when TheHive & Cortex 2 share the same URL [\#70](https://github.com/TheHive-Project/Cortex/issues/70)

## [2.0.1](https://github.com/TheHive-Project/Cortex/tree/2.0.1) (2018-03-30)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/2.0.0...2.0.1)

**Fixed bugs:**

- File upload component not working [\#69](https://github.com/TheHive-Project/Cortex/issues/69)
- Packages contain obsolete configuration sample [\#68](https://github.com/TheHive-Project/Cortex/issues/68)
- User can't change his password [\#67](https://github.com/TheHive-Project/Cortex/issues/67)

## [2.0.0](https://github.com/TheHive-Project/Cortex/tree/2.0.0) (2018-03-30)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/1.1.4...2.0.0)

**Implemented enhancements:**

- Display analyzers only if necessary configuration values are set [\#14](https://github.com/TheHive-Project/Cortex/issues/14)

**Fixed bugs:**

- Error when clicking out of the "New Analysis" box [\#48](https://github.com/TheHive-Project/Cortex/issues/48)

**Closed issues:**

- AMD64 REPO 404 [\#64](https://github.com/TheHive-Project/Cortex/issues/64)
- Unable for Cortex to connected to MISP [\#61](https://github.com/TheHive-Project/Cortex/issues/61)
- Cortex crashed after a OutOfMemoryError [\#60](https://github.com/TheHive-Project/Cortex/issues/60)
- Malwareconfig Lookup and Yara Rule Additions [\#57](https://github.com/TheHive-Project/Cortex/issues/57)
- Shodan Analyzer Fails - Module cortexutils Not Found [\#55](https://github.com/TheHive-Project/Cortex/issues/55)
- API: Resource not found by Assets controller [\#47](https://github.com/TheHive-Project/Cortex/issues/47)
- Wrong MISP config in conf/application.sample [\#45](https://github.com/TheHive-Project/Cortex/issues/45)
- Local, LDAP, AD and API Key Authentication [\#7](https://github.com/TheHive-Project/Cortex/issues/7)
- Limit Rates and Respect Quotas [\#6](https://github.com/TheHive-Project/Cortex/issues/6)
- Persistence and Report Caching [\#5](https://github.com/TheHive-Project/Cortex/issues/5)
- Provide alternative paths for analyzers in addition to standard path.  [\#4](https://github.com/TheHive-Project/Cortex/issues/4)
- Provide way to reload conf file for new API keys without shutdown. [\#3](https://github.com/TheHive-Project/Cortex/issues/3)
- Provide Secret Key auth to upstream service [\#2](https://github.com/TheHive-Project/Cortex/issues/2)

**Merged pull requests:**

- Add proxy configuration block [\#52](https://github.com/TheHive-Project/Cortex/pull/52) ([cemasirt](https://github.com/cemasirt))
- Fixed Typo [\#46](https://github.com/TheHive-Project/Cortex/pull/46) ([steoleary](https://github.com/steoleary))
- Adding WOT config sample [\#43](https://github.com/TheHive-Project/Cortex/pull/43) ([mthlvt](https://github.com/mthlvt))

## [1.1.4](https://github.com/TheHive-Project/Cortex/tree/1.1.4) (2017-09-15)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/1.1.3...1.1.4)

**Fixed bugs:**

- Display a error notification on analyzer start fail [\#39](https://github.com/TheHive-Project/Cortex/issues/39)
- Cortex removes the input details from failure reports [\#38](https://github.com/TheHive-Project/Cortex/issues/38)

**Closed issues:**

- Group ownership in Docker image prevents running on OpenShift [\#42](https://github.com/TheHive-Project/Cortex/issues/42)
- Disable analyzer in configuration file [\#32](https://github.com/TheHive-Project/Cortex/issues/32)

## [1.1.3](https://github.com/TheHive-Project/Cortex/tree/1.1.3) (2017-06-14)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/debian/1.1.2-2...1.1.3)

**Fixed bugs:**

- Problem Start Cortex on Ubuntu 16.04 [\#35](https://github.com/TheHive-Project/Cortex/issues/35)
- Error when parsing analyzer failure report [\#33](https://github.com/TheHive-Project/Cortex/issues/33)

## [debian/1.1.2-2](https://github.com/TheHive-Project/Cortex/tree/debian/1.1.2-2) (2017-05-24)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/1.1.2...debian/1.1.2-2)

## [1.1.2](https://github.com/TheHive-Project/Cortex/tree/1.1.2) (2017-05-24)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/debian/1.1.1-2...1.1.2)

**Implemented enhancements:**

- Add page loader [\#30](https://github.com/TheHive-Project/Cortex/issues/30)
- Initialize MISP modules at startup [\#28](https://github.com/TheHive-Project/Cortex/issues/28)

**Fixed bugs:**

- jobstatus from jobs within cortex are not updated when status changes [\#31](https://github.com/TheHive-Project/Cortex/issues/31)
- Cortex and MISP unclear and error-loop [\#29](https://github.com/TheHive-Project/Cortex/issues/29)
- Error 500 in TheHive when a job is submited to Cortex [\#27](https://github.com/TheHive-Project/Cortex/issues/27)

## [debian/1.1.1-2](https://github.com/TheHive-Project/Cortex/tree/debian/1.1.1-2) (2017-05-19)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/rpm/1.1.1-2...debian/1.1.1-2)

## [rpm/1.1.1-2](https://github.com/TheHive-Project/Cortex/tree/rpm/1.1.1-2) (2017-05-19)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/1.1.1...rpm/1.1.1-2)

**Fixed bugs:**

- After Upgrade from Cortex 1.0.2 to 1.1.1 system does not come up [\#26](https://github.com/TheHive-Project/Cortex/issues/26)

## [1.1.1](https://github.com/TheHive-Project/Cortex/tree/1.1.1) (2017-05-17)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/1.1.0...1.1.1)

**Fixed bugs:**

- Missing logos and favicons [\#25](https://github.com/TheHive-Project/Cortex/issues/25)

**Closed issues:**

- Cortex 1.1.0 doesnt work with theHive 2.11.0 [\#24](https://github.com/TheHive-Project/Cortex/issues/24)
- MISP integration [\#21](https://github.com/TheHive-Project/Cortex/issues/21)

## [1.1.0](https://github.com/TheHive-Project/Cortex/tree/1.1.0) (2017-05-12)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/1.0.2...1.1.0)

**Implemented enhancements:**

- Add support to .deb and .rpm package generation [\#20](https://github.com/TheHive-Project/Cortex/issues/20)
- Scala code cleanup [\#19](https://github.com/TheHive-Project/Cortex/issues/19)
- Display analyzers metadata [\#18](https://github.com/TheHive-Project/Cortex/issues/18)

**Closed issues:**

- Display Cortex version on the footer [\#23](https://github.com/TheHive-Project/Cortex/issues/23)
- Use new logo and favicon [\#22](https://github.com/TheHive-Project/Cortex/issues/22)

## [1.0.2](https://github.com/TheHive-Project/Cortex/tree/1.0.2) (2017-04-19)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/1.0.1...1.0.2)

**Fixed bugs:**

- Redirect to jobs list when a job is not found [\#16](https://github.com/TheHive-Project/Cortex/issues/16)
- Global section in configuration file is ignored [\#13](https://github.com/TheHive-Project/Cortex/issues/13)
- Secure the usage of angular-ui-notification library [\#12](https://github.com/TheHive-Project/Cortex/issues/12)
- Jobs list API doesn't take into account the limit param [\#11](https://github.com/TheHive-Project/Cortex/issues/11)

**Closed issues:**

- Support for cuckoo malware analysis plattform \(link analysis\) [\#17](https://github.com/TheHive-Project/Cortex/issues/17)
- Documentation on 'How to create an analyzer' [\#10](https://github.com/TheHive-Project/Cortex/issues/10)

## [1.0.1](https://github.com/TheHive-Project/Cortex/tree/1.0.1) (2017-03-08)
[Full Changelog](https://github.com/TheHive-Project/Cortex/compare/1.0.0...1.0.1)

**Fixed bugs:**

- Fix page scroll issues [\#9](https://github.com/TheHive-Project/Cortex/issues/9)

**Closed issues:**

- Missing install repertory [\#1](https://github.com/TheHive-Project/Cortex/issues/1)

## [1.0.0](https://github.com/TheHive-Project/Cortex/tree/1.0.0) (2017-02-01)


\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*