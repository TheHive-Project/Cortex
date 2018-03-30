'use strict';

export default function(app) {
  function NotificationServiceProvider() {
    let loginState = 'login';
    let maintenanceState = 'maintenance';

    this.setLoginState = function(state) {
      loginState = state;
    };

    this.setMaintenanceState = function(state) {
      maintenanceState = state;
    };

    function NotificationService($state, HtmlSanitizer, Notification) {
      'ngInject';

      this.success = function(message) {
        let sanitized = HtmlSanitizer.sanitize(message);

        return Notification.success(sanitized);
      };
      this.error = function(message) {
        let sanitized = HtmlSanitizer.sanitize(message);

        return Notification.error(sanitized);
      };
      this.log = function(message, type) {
        Notification[type || 'error'](HtmlSanitizer.sanitize(message));
      };
      this.handleError = function(moduleName, data, status) {
        if (status === 401) {
          $state.go(loginState);
        } else if (status === 520) {
          $state.go(maintenanceState);
        } else if (angular.isString(data) && data !== '') {
          this.log(moduleName + ': ' + data, 'error');
        } else if (angular.isObject(data)) {
          this.log(moduleName + ': ' + data.message, 'error');
        }
      };
    }

    this.$get = function($state, HtmlSanitizer, Notification) {
      'ngInject';

      return new NotificationService($state, HtmlSanitizer, Notification);
    };
  }

  app.provider('NotificationService', NotificationServiceProvider);
}
