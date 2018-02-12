'use strict';

import _ from 'lodash/core';

export default class JobsListController {
  constructor($log, JobService, NotificationService, ModalService) {
    'ngInject';

    this.$log = $log;
    this.JobService = JobService;
    this.NotificationService = NotificationService;
    this.ModalService = ModalService;
  }

  deleteJob(id) {
    let modalInstance = this.ModalService.confirm(
      'Delete job',
      `Are your sure you want to delete this job?`,
      {
        flavor: 'danger',
        okText: 'Yes, delete job'
      }
    );

    modalInstance.result
      .then(() => this.JobService.remove(id))
      .then(
        /*response*/
        () => {
          this.onDelete();
          this.NotificationService.success('Job removed successfully');
        }
      )
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to delete the Job.');
        }
      });
  }
}
