'use strict';

import SettingsPageController from './settings.page.controller';
import settingsPageTpl from './settings.page.html';

const settingsModule = angular
  .module('settings-module', ['images-resizer', 'naif.base64'])
  .config($stateProvider => {
    'ngInject';

    $stateProvider.state('main.settings', {
      url: 'settings',
      component: 'settingsPage'
    });
  })
  .component('settingsPage', {
    controller: SettingsPageController,
    templateUrl: settingsPageTpl,
    require: {
      main: '^mainPage'
    }
  });

export default settingsModule;
