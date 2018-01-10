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

import analyzerService from './services/AnalyzerService';

validationTestDirective(shared);
fixedHeightDirective(shared);

authService(shared);
userService(shared);
htmlSanitizer(shared);
notificationService(shared);
streamService(shared);
versionService(shared);

analyzerService(shared);

constants(shared);
storeFactory(shared);
resolverProvider(shared);

export default shared;
