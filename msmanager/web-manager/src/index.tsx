import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import 'react-perfect-scrollbar/dist/css/styles.css';
import * as serviceWorker from './serviceWorker';
import Root from "./containers/Root";
import {BrowserRouter} from "react-router-dom";
import configureStore from "./store/configureStore";

// TODO implement labelToIcon function
// TODO hide search bar when not needed
// TODO push footer to end of page

const store = configureStore();

ReactDOM.render(
    <BrowserRouter>
        <Root store={store}/>
    </BrowserRouter>,
    document.getElementById('root'));

// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: https://bit.ly/CRA-PWA
serviceWorker.unregister();