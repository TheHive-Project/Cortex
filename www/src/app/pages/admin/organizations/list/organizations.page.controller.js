'use strict';

import _ from 'lodash/core';

export default class OrganizationsPageController {
  constructor($log, OrganizationService) {
    'ngInject';

    this.$log = $log;
    this.OrganizationService = OrganizationService;
  }

  $onInit() {
    this.state = {
      showForm: false,
      formData: {}
    };
  }

  create() {
    this.OrganizationService.create(
      _.pick(this.state.formData, ['name', 'description'])
    ).then(response => {
      this.$log.log('Organization created', response);
      this.reload();
      this.$onInit();
    });
  }

  disable(id) {
    this.OrganizationService.disable(id).then(response => {
      this.$log.log(`Organization ${id} deleted`, response);
      this.reload();
    });
  }

  enable(id) {
    this.OrganizationService.enable(id).then(response => {
      this.$log.log(`Organization ${id} deleted`, response);
      this.reload();
    });
  }

  reload() {
    this.OrganizationService.list().then(
      organizations => (this.organizations = organizations)
    );
  }
}
