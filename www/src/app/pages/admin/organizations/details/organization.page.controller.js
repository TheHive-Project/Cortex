'use strict';

import _ from 'lodash';

export default class OrganizationPageController {
  constructor($log, OrganizationService) {
    'ngInject';

    this.$log = $log;
    this.OrganizationService = OrganizationService;
  }

  reloadUsers() {
    this.OrganizationService.users(this.organization.id).then(
      response => (this.users = response)
    );
  }
}
