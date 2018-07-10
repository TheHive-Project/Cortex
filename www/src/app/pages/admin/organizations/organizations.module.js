'use strict';

import angular from 'angular';

import OrganizationsPageController from './list/organizations.page.controller';
import organizationsPageTpl from './list/organizations.page.html';

import OrganizationPageController from './details/organization.page.controller';
import organizationPageTpl from './details/organization.page.html';

// Users
import OrganizationUsersController from './components/users-list.controller';
import organizationUsersTpl from './components/users-list.html';

// Analyzers 
import AnalyzerConfigFormController from './components/analyzers/analyzer-config-form.controller';
import analyzerConfigFormTpl from './components/analyzers/analyzer-config-form.html';

import OrganizationAnalyzersController from './components/analyzers/analyzers-list.controller';
import organizationAnalyzersTpl from './components/analyzers/analyzers-list.html';

import OrganizationConfigsController from './components/analyzers/config-list.controller';
import organizationConfigsTpl from './components/analyzers/config-list.html';

import ConfigurationForm from './components/config-form.controller';
import configurationFormTpl from './components/config-form.html';

// Responders
import ResponderConfigFormController from './components/responders/responder-config-form.controller';
import responderConfigFormTpl from './components/responders/responder-config-form.html';

import OrganizationRespondersController from './components/responders/responders-list.controller';
import organizationRespondersTpl from './components/responders/responders-list.html';

import OrganizationReponderConfigsController from './components/responders/config-list.controller';
import organizationResponderConfigsTpl from './components/responders/config-list.html';

import organizationService from './organizations.service';

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
          analyzersConfigurations: (AuthService, AnalyzerService, $q) => {
            if (AuthService.hasRole([Roles.ORGADMIN])) {
              return AnalyzerService.configurations();
            } else {
              return $q.resolve([]);
            }
          },
          respondersConfigurations: (AuthService, ResponderService, $q) => {
            if (AuthService.hasRole([Roles.ORGADMIN])) {
              return ResponderService.configurations();
            } else {
              return $q.resolve([]);
            }
          },
          responderDefinitions: (AuthService, ResponderService, $q) => {
            if (AuthService.hasRole([Roles.ORGADMIN])) {
              return ResponderService.definitions();
            } else {
              return $q.resolve({});
            }
          },
          responders: (AuthService, OrganizationService, $q) => {
            if (AuthService.hasRole([Roles.ORGADMIN])) {
              return OrganizationService.responders();
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
      analyzersConfigurations: '<',
      responderDefinitions: '<',
      responders: '<',
      respondersConfigurations: '<'
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
  .component('organizationRespondersList', {
    controller: OrganizationRespondersController,
    templateUrl: organizationRespondersTpl,
    bindings: {
      organization: '<',
      responderDefinitions: '<',
      responders: '<'
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
  .component('organizationResponderConfigList', {
    controller: OrganizationReponderConfigsController,
    templateUrl: organizationResponderConfigsTpl,
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
      definition: '=',
      configuration: '<',
      globalConfig: '<',
      baseConfig: '<'
    }
  })
  .component('responderConfigForm', {
    controller: ResponderConfigFormController,
    templateUrl: responderConfigFormTpl,
    bindings: {
      responder: '=',
      definition: '=',
      configuration: '<',
      globalConfig: '<',
      baseConfig: '<'
    }
  })
  .service('OrganizationService', organizationService);

export default organizationsModule;