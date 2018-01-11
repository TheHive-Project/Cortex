'use strict';

import angular from 'angular';

import OrganizationsPageController from './list/organizations.page.controller';
import organizationsPageTpl from './list/organizations.page.html';

import OrganizationPageController from './details/organization.page.controller';
import organizationPageTpl from './details/organization.page.html';

import organizationService from './organizations.service.js';

import './organizations.scss';

const organizationsModule = angular
  .module('organizations-module', ['ui.router'])
  .config($stateProvider => {
    'ngInject';
    $stateProvider
      .state('main.organizations', {
        url: 'admin/organizations',
        component: 'organizationsPage',
        resolve: {
          organizations: OrganizationService => OrganizationService.list()
        }
      })
      .state('main.organization', {
        url: 'admin/organizations/{id}',
        component: 'organizationPage',
        resolve: {
          organization: ($stateParams, OrganizationService) =>
            OrganizationService.getById($stateParams.id),
          analyzerDefinitions: AnalyzerService => AnalyzerService.definitions(),
          analyzers: ($stateParams, OrganizationService) =>
            OrganizationService.analyzers($stateParams.id)
        }
      });
  })
  .component('organizationsPage', {
    controller: OrganizationsPageController,
    templateUrl: organizationsPageTpl,
    bindings: {
      organizations: '<'
    }
  })
  .component('organizationPage', {
    controller: OrganizationPageController,
    templateUrl: organizationPageTpl,
    bindings: {
      organization: '<',
      analyzerDefinitions: '<',
      analyzers: '<'
    }
  })
  .service('OrganizationService', organizationService);

export default organizationsModule;
