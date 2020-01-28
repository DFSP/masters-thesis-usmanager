/*
 * MIT License
 *
 * Copyright (c) 2020 msmanager
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import {
   /* BREADCRUMBS_ADD,*/
    BREADCRUMBS_UPDATE,
    RESET_ERROR_MESSAGE,
    SEARCH_UPDATE, SELECT_ENTITY,
    SIDENAV_SHOW_USER,
    SIDENAV_SHOW_WIDTH
} from "../actions";
import {loadingBarReducer} from "react-redux-loading-bar";
import {IBreadcrumbs} from "../components/shared/Breadcrumbs";
import { merge } from "lodash";
import {act} from "react-dom/test-utils";

export const loadingBar = loadingBarReducer;

export const sidenav = (
    state = { user: true, width: window.innerWidth > 992 },
    action: { type: string, value: boolean },
) => {
    const { type, value } = action;
    switch (type) {
        case SIDENAV_SHOW_USER:
            return {
                ...state,
                user: value
            };
        case SIDENAV_SHOW_WIDTH:
            return {
                ...state,
                width: value
            };
        default:
            return state;
    }
};

export const search = (state = "", action: { type: string, search: string}) => {
    const { type, search } = action;
    switch (type) {
        case SEARCH_UPDATE:
            return search;
        default:
            return state;
    }
};

/*export const breadcrumbs = (state = {}, action: { type: string, breadcrumbs: IBreadcrumbs}) => {
    const { type, breadcrumbs } = action;
    switch (type) {
        case BREADCRUMBS_UPDATE:
            return breadcrumbs;
        default:
            return state;
    }
};*/

export function breadcrumbs(state: IBreadcrumbs = [], action: { type: string, title: string, link?: string, breadcrumbs: IBreadcrumbs }) {
    const { type, title, link, breadcrumbs } = action;
    /*if (type === BREADCRUMBS_ADD) {
        state.push({title, link});
    }
    else*/ if (type === BREADCRUMBS_UPDATE) {
        state = breadcrumbs;
    }
    return state;
}

export const errorMessage = (state = null, action: {type: string, error: string}) => {
    const { type, error } = action;
    if (type === RESET_ERROR_MESSAGE) {
        return null
    } else if (error) {
        return error
    }
    return state
};

export const loading = (state = true, action: any) => {
    if (action.response && action.response.entities) {
        return false;
    }
    return state;
};

export function select<T>(state = {}, action: {type: string, entity: T}) {
    const { type, entity } = action;
    switch (type) {
        case SELECT_ENTITY:
            return merge({}, state, entity);
        default:
            return state;
    }
}