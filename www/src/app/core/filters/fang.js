'use strict';

export default function(app) {
  app.filter('fang', UtilsService => function(value) {
      if (!value) {
        return '';
      }

      return UtilsService.fangValue(value);
    });
}
