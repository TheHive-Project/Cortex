'use strict';

import angular from 'angular';

import OrganizationsPageController from './list/organizations.page.controller';
import organizationsPageTpl from './list/organizations.page.html';

import OrganizationPageController from './details/organization.page.controller';
import organizationPageTpl from './details/organization.page.html';

import AnalyzerConfigFormController from './components/analyzer-config-form.controller';
import analyzerConfigFormTpl from './components/analyzer-config-form.html';

import OrganizationAnalyzersController from './components/analyzers-list.controller';
import organizationAnalyzersTpl from './components/analyzers-list.html';

import OrganizationUsersController from './components/users-list.controller';
import organizationUsersTpl from './components/users-list.html';

import OrganizationConfigsController from './components/config-list.controller';
import organizationConfigsTpl from './components/config-list.html';

import ConfigurationForm from './components/config-form.controller';
import configurationFormTpl from './components/config-form.html';

import organizationService from './organizations.service.js';

import './organizations.scss';

const organizationsModule = angular
  .module('organizations-module', ['ui.router'])
  .config(($stateProvider, Roles) => {
    'ngInject';
    $stateProvider
      .state('main.organizations', {
        url: 'admin/organizations',
        component: 'organizationsPage',
        data: {
          allow: [Roles.SUPERADMIN]
        }
      })
      .state('main.organization', {
        url: 'admin/organizations/{id}',
        component: 'organizationPage',
        resolve: {
          organization: ($stateParams, OrganizationService) =>
            OrganizationService.getById($stateParams.id),
          users: ($stateParams, OrganizationService) =>
            OrganizationService.users($stateParams.id),
          analyzerDefinitions: (AuthService, AnalyzerService, $q) => {
            if (AuthService.hasRole([Roles.ORGADMIN])) {
              return AnalyzerService.definitions();
            } else {
              return $q.resolve({});
            }
          },
          analyzers: (AuthService, OrganizationService, $q) => {
            if (AuthService.hasRole([Roles.ORGADMIN])) {
              return OrganizationService.analyzers();
            } else {
              return $q.resolve([]);
            }
          },
          configurations: (AuthService, AnalyzerService, $q) => {
            if (AuthService.hasRole([Roles.ORGADMIN])) {
              return AnalyzerService.configurations();
            } else {
              return $q.resolve([]);
            }
          }
        },
        data: {
          allow: [Roles.SUPERADMIN, Roles.ORGADMIN]
        }
      });
  })
  .component('organizationsPage', {
    controller: OrganizationsPageController,
    templateUrl: organizationsPageTpl
  })
  .component('organizationPage', {
    controller: OrganizationPageController,
    templateUrl: organizationPageTpl,
    bindings: {
      organization: '<',
      users: '<',
      analyzerDefinitions: '<',
      analyzers: '<',
      configurations: '<'
    }
  })
  .component('organizationAnalyzersList', {
    controller: OrganizationAnalyzersController,
    templateUrl: organizationAnalyzersTpl,
    bindings: {
      organization: '<',
      analyzerDefinitions: '<',
      analyzers: '<'
    }
  })
  .component('organizationUsersList', {
    controller: OrganizationUsersController,
    templateUrl: organizationUsersTpl,
    bindings: {
      organization: '<'
    }
  })
  .component('organizationConfigList', {
    controller: OrganizationConfigsController,
    templateUrl: organizationConfigsTpl,
    bindings: {
      organization: '<',
      configurations: '<'
    }
  })
  .component('configurationForm', {
    controller: ConfigurationForm,
    templateUrl: configurationFormTpl,
    bindings: {
      items: '<',
      configuration: '<'
    }
  })
  .component('analyzerConfigForm', {
    controller: AnalyzerConfigFormController,
    templateUrl: analyzerConfigFormTpl,
    bindings: {
      analyzer: '=',
      definition: '='
    }
  })
  .service('OrganizationService', organizationService);

export default organizationsModule;
