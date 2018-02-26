'use strict';

import _ from 'lodash';

export default class OrganizationPageController {
  constructor(
    $log,
    $stateParams,
    AnalyzerService,
    OrganizationService,
    AuthService
  ) {
    'ngInject';

    this.$log = $log;
    this.orgId = $stateParams.id;
    this.AnalyzerService = AnalyzerService;
    this.OrganizationService = OrganizationService;
    this.AuthService = AuthService;
  }

  $onInit() {
    // if (this.AuthService.hasRole(['superadmin'])) {
    //   this.AnalyzerService.definitions().then(
    //     defs => (this.analyzerDefinitions = defs)
    //   );
    //   this.OrganizationService.analyzers().then(
    //     analyzers => (this.analyzers = analyzers)
    //   );
    // }
    // this.OrganizationService.users(this.orgId).then(
    //   users => (this.users = users)
    // );
  }

  reloadUsers() {
    this.OrganizationService.users(this.organization.id).then(
      response => (this.users = response)
    );
  }
}
