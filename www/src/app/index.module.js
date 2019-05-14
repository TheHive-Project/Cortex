'use strict';

import config from './index.config';
import run from './index.run';

import uiRouter from '@uirouter/angularjs';

import coreModule from './core/core.module';
import indexComponents from './index.components';

import mainModule from './pages/main/main.module';
import loginModule from './pages/login/login.module';
import analyzersModule from './pages/analyzers/analyzers.module';
import respondersModule from './pages/responders/responders.module';
import jobsModule from './pages/jobs/jobs.module';
import settingsModule from './pages/settings/settings.module';

import adminModule from './pages/admin/admin.module';

import maintenanceModule from './pages/maintenance/maintenance.module';

const App = angular.module('cortex', [
  // plugins
  uiRouter,
  'ngSanitize',
  'ngMessages',
  'ngResource',
  'ui.bootstrap',
  'ui-notification',
  'angularUtils.directives.dirPagination',
  'angularMoment',
  'angular-clipboard',
  'btorfs.multiselect',
  'LocalStorageModule',
  'angularUtils.directives.dirPagination',
  'ui.utils.masks',

  // core
  coreModule.name,

  // components
  indexComponents.name,

  // pages
  mainModule.name,
  loginModule.name,
  maintenanceModule.name,
  analyzersModule.name,
  respondersModule.name,
  jobsModule.name,
  adminModule.name,
  settingsModule.name
]);

App.config(config).run(run);

export default App;