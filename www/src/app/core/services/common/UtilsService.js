'use strict';

export default function(app) {
  app.service('UtilsService', function() {
    this.sensitiveTypes = ['url', 'ip', 'mail', 'domain', 'filename'];

    this.fangValue = function(value) {
      return value
        .replace(/\[\.\]/g, '.')
        .replace(/hxxp/gi, 'http')
        .replace(/\./g, '[.]')
        .replace(/http/gi, 'hxxp');
    };

    this.fang = function(observable) {
      if (this.sensitiveTypes.indexOf(observable.dataType) === -1) {
        return observable.data;
      }

      return this.fangValue(observable.data);
    };

    this.unfang = function(observable) {
      return observable.data.replace(/\[\.\]/g, '.').replace(/hxxp/gi, 'http');
    };
  });
}
