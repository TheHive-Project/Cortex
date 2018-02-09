'use strict';

import modalConfirmTpl from './modal.confirm.html';

class ModalConfirmController {
  constructor($uibModalInstance, title, message, config) {
    'ngInject';

    this.$uibModalInstance = $uibModalInstance;
    this.title = title;
    this.message = message;
    this.config = config;
  }
  cancel() {
    this.$uibModalInstance.dismiss('cancel');
  }
  confirm() {
    this.$uibModalInstance.close('ok');
  }
}

export default class ModalService {
  constructor($log, $uibModal) {
    'ngInject';

    this.$log = $log;
    this.$uibModal = $uibModal;
  }

  /**
   * @param {*} title: Title of the modal
   * @param {*} message: Content of the modal
   * @param {Object} config: customization of the modal: flavor, okText
   */
  confirm(title, message, config) {
    return this.$uibModal.open({
      controller: ModalConfirmController,
      templateUrl: modalConfirmTpl,
      controllerAs: '$modal',
      resolve: {
        title: function() {
          return title;
        },
        message: function() {
          return message;
        },
        config: function() {
          return config || {};
        }
      }
    });
  }
}
