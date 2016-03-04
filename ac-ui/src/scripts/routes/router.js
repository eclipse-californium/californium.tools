/*global define*/

define([
    'jquery',
    'backbone',
    'views/layout',
    'views/fullscreen',
    'views/actinium_path',
    'views/home',
    'views/app_instance',
    'views/app',
    'views/lib',
    'views/test'
], function($, Backbone, LayoutView, FullscreenView, ActiniumPathView, HomeView, AppInstanceView, AppView, LibView, TestView) {
    'use strict';

    var RouterRouter = Backbone.Router.extend({
        routes: {
            '': 'actiniumPath',
            'home': 'home',
            'app_instance/:name': 'app_instance',
            'app_instance/:name/test': 'test',
            'app/:name': 'app',
            'lib/:name': 'lib',
            'newlib': 'lib',
            'new': 'app'
        },

        views: {
            'ActiniumPath': ActiniumPathView,
            'Home': HomeView,
            'AppInstance': AppInstanceView,
            'App': AppView,
            'Lib': LibView,
            'Test': TestView
        },

        current_layout: null,

        last_hash: null,

        modal: null,

        current_view: null,

        layouts: {
            'main': function() {
                return new LayoutView();
            },
            'fullscreen': function() {
                return new FullscreenView();
            }
        },

        actiniumPath: function(argument) {
            this.loadView('fullscreen', 'ActiniumPath', arguments);
        },

        home: function(argument) {
            this.loadView('main', 'Home', arguments);
        },

        app: function(argument) {
            this.loadView('main', 'App', arguments);
        },

        lib: function(argument) {
            this.loadView('main', 'Lib', arguments);
        },

        app_instance: function(argument) {
            this.loadView('main', 'AppInstance', arguments);
        },

        test: function(argument) {
            this.loadView('main', 'Test', arguments);
        },

        loadView: function(layout, view, attr) {
            if (!window.base_url) {
                window.Router.navigate("/", true);
            }
            if (this.modal !== null) {
                this.modal.$el.modal('hide');
                this.modal.dispose();
                this.modal = null;
            }
            if (this.last_hash == window.location.hash) {
                return;
            }
            if (this.view && this.view.has_unsaved && this.view.has_unsaved() && !confirm("Do you want to discard all unsaved changes?")) {
                window.location.hash = this.last_hash;
                return;
            }
            this.last_hash = window.location.hash;
            if (this.current_view == view) {
                this.view.set_attr(attr);
                return;
            }
            this.view && this.view.dispose();
            if (this.current_layout !== layout) {
                this.current_layout = layout;
                this.layout && this.layout.dispose();
                this.layout = this.layouts[layout]();
                $("#app").html(this.layout.$el);
                this.layout.render();
            }
            this.view = new this.views[view]();
            this.view.set_attr(attr);
            this.current_view = view;
            this.layout.$el.find("#content").html(this.view.$el);
            this.view.render();
        }

    });

    return RouterRouter;
});
