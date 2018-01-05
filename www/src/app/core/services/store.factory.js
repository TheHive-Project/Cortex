'use strict';

export default function (app) {
    app
        .factory('store', storeFactory);

        function storeFactory () {
            return {
                "countries": ["USA", "UK", "Ukraine"]
            };
        }
}
