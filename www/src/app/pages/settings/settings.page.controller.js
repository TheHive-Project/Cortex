'use strict';

export default class SettingsPageController {
  constructor(
    $log,
    $scope,
    $state,
    UserService,
    ModalService,
    NotificationService,
    resizeService,
    clipboard
  ) {
    'ngInject';

    this.$log = $log;
    this.$scope = $scope;
    this.$state = $state;

    this.UserService = UserService;
    this.ModalService = ModalService;
    this.NotificationService = NotificationService;
    this.resizeService = resizeService;
    this.clipboard = clipboard;

    this.basicData = {};
    this.passData = {
      changePassword: false,
      currentPassword: '',
      password: '',
      passwordConfirm: ''
    };
    this.keyData = {};
  }

  $onInit() {
    this.config = this.main.config;
    this.currentUser = this.main.currentUser;

    this.basicData = {
      username: this.currentUser.id,
      name: this.currentUser.name,
      avatar: this.currentUser.avatar
    };

    this.canChangePass =
      this.config.config.capabilities.indexOf('changePassword') !== -1;

    this.canChangeKey =
      this.config.config.capabilities.indexOf('authByKey') !== -1;

    this.$scope.$watch('$ctrl.avatar', value => {
      if (!value) {
        return;
      }

      this.resizeService
        .resizeImage('data:' + value.filetype + ';base64,' + value.base64, {
          height: 100,
          width: 100,
          outputFormat: 'image/jpeg'
        })
        .then(image => {
          this.basicData.avatar = image.replace('data:image/jpeg;base64,', '');
        });
    });
  }

  updateBasicInfo(form) {
    if (!form.$valid) {
      return;
    }

    this.UserService.update(this.currentUser.id, {
      name: this.basicData.name,
      avatar: this.basicData.avatar
    })
      .then(data => {
        this.currentUser.name = data.name;
        this.UserService.updateCache(data._id, data);

        this.NotificationService.log(
          'Your basic information have been successfully updated',
          'success'
        );

        this.$state.reload();
      })
      .catch(err => {
        this.NotificationService.error('SettingsCtrl', err.data, err.status);
      });
  }

  updatePassword(form) {
    if (!form.$valid) {
      return;
    }

    if (
      this.passData.currentPassword &&
      this.passData.password !== '' &&
      this.passData.password === this.passData.passwordConfirm
    ) {
      this.UserService.changePass(
        this.currentUser.id,
        this.passData.currentPassword,
        this.passData.password
      )
        .then(() => {
          this.NotificationService.log(
            'Your password has been successfully updated',
            'success'
          );
          this.$state.reload();
        })
        .catch(response => {
          this.NotificationService.error(
            'SettingsCtrl',
            response.data,
            response.status
          );
        });
    } else {
      this.$state.go('index');
    }
  }
  clearPassword(form, changePassword) {
    if (!changePassword) {
      this.passData.currentPassword = '';
      this.passData.password = '';
      this.passData.passwordConfirm = '';
    }

    form.$setValidity('currentPassword', true);
    form.$setValidity('password', true);
    form.$setValidity('passwordConfirm', true);
    form.$setPristine(true);
  }

  cancel() {
    this.$state.go('index');
  }

  clearAvatar(form) {
    this.basicData.avatar = null;
    form.avatar.$setValidity('maxsize', true);
    form.avatar.$setPristine(true);
  }

  getKey() {
    this.UserService.getKey(this.currentUser.id).then(key => {
      this.keyData.key = key;
    });
  }

  createKey() {
    let modalInstance = this.ModalService.confirm(
      'Renew API Key',
      'Are you sure you want to renew your API Key?',
      {
        flavor: 'danger',
        okText: 'Yes, renew it'
      }
    );

    modalInstance.result
      .then(() => this.UserService.setKey(this.currentUser.id))
      .then(() => {
        delete this.keyData.key;
        this.NotificationService.success(
          'API key has been successfully renewed.'
        );
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to lock the user.');
        }
      });
  }

  copyKey() {
    this.clipboard.copyText(this.keyData.key);
    delete this.keyData.key;
    this.NotificationService.success(
      'API key has been successfully copied to clipboard.'
    );
  }
}
