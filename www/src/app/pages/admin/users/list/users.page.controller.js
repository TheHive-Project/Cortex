'use strict';

export default class UsersPageController {
  constructor($log, UserService) {
    'ngInject';

    this.$log = $log;
    this.UserService = UserService;
  }

  reload() {
    return this.UserService.list().then(response => (this.users = response));
  }
}
