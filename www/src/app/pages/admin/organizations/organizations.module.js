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
          allow: [Roles.ADMIN]
        }
      })
      .state('main.organization', {
        url: 'admin/organizations/{id}',
        component: 'organizationPage',
        resolve: {
          analyzerDefinitions: AnalyzerService => AnalyzerService.definitions(),
          organization: ($stateParams, OrganizationService) =>
            OrganizationService.getById($stateParams.id),
          analyzers: ($stateParams, OrganizationService) =>
            OrganizationService.analyzers($stateParams.id),
          users: ($stateParams, OrganizationService) =>
            OrganizationService.users($stateParams.id)
        },
        data: {
          allow: [Roles.ADMIN]
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
      analyzerDefinitions: '<',
      analyzers: '<',
      users: '<'
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
