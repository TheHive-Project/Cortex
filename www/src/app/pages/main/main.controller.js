'use strict';

export default class MainController {
  constructor($log, $state) {
    'ngInject';
    this.$log = $log;
    this.$state = $state;
  }

  $onInit() {
    if (this.currentUser === 520) {
      this.$state.go('maintenance');
      return;
    } else if (!this.currentUser || !this.currentUser.id) {
      this.$state.go('login', {
        autoLogin: (this.config || {}).ssoAutoLogin
      });
      return;
    }
  }
}