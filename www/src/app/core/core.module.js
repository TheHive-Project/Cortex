'use strict';

const shared = angular.module('core.shared', []);

import validationTestDirective from './directives/validation-test/validation-test.directive';
import fixedHeightDirective from './directives/fixed-height/fixed-height.directive';

import constants from './services/constants';
import storeFactory from './services/store.factory';
import resolverProvider from './services/resolver.provider';

import authService from './services/common/AuthService';
import userService from './services/common/UserService';
import htmlSanitizer from './services/common/HtmlSanitizer';
import notificationService from './services/common/NotificationService';
import streamService from './services/common/StreamService';
import versionService from './services/common/VersionService';
import utilsService from './services/common/UtilsService';

import fangFilter from './filters/fang';

import analyzerService from './services/AnalyzerService';
import jobService from './services/JobService';

validationTestDirective(shared);
fixedHeightDirective(shared);

/* Common services */
authService(shared);
userService(shared);
htmlSanitizer(shared);
notificationService(shared);
streamService(shared);
versionService(shared);
utilsService(shared);

/* App services */
analyzerService(shared);
jobService(shared);

constants(shared);
storeFactory(shared);
resolverProvider(shared);

/* Filters */
fangFilter(shared);

export default shared;
