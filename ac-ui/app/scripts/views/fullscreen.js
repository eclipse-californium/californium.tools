/*global define*/

define([
    'jquery',
    'underscore',
    'backbone',
    'views/base',
    'templates'
], function($, _, Backbone, BaseView, JST) {
    'use strict';

    var FullscreenView = BaseView.extend({
        template: JST['app/scripts/templates/fullscreen.ejs']
    });

    return FullscreenView;
});
