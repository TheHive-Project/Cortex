'use strict';

export default function (app) {
    app.directive('nullIfEmpty', nullIfEmpty);

    function nullIfEmpty() {
        return {
            require: 'ngModel',
            link: function (scope, el, attrs, ngModel) {
                ngModel.$parsers.push(function (value) {
                    return value === '' ? null : value;
                });

                ngModel.$formatters.push(function (value) {
                    return !value ? '' : value;
                });
            }
        };
    }
}
