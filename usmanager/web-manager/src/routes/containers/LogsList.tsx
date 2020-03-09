import BaseComponent from "../../components/BaseComponent";
import ListItem from "../../components/list/ListItem";
import styles from "./LogsList.module.css";
import List from "../../components/list/List";
import React from "react";

interface PortsListProps {
  logs: string[];
}

type Props = PortsListProps;

export default class LogsList extends BaseComponent<Props, {}> {

  private logs = (logs: string, index: number): JSX.Element =>
    <ListItem key={index}>
      <div className={styles.itemContent}>
        <span>{logs}</span>
      </div>
    </ListItem>;

  render() {
    const LogsList = List<string>();
    return (
      <LogsList emptyMessage={`No logs available`}
                 list={this.props.logs}
                 show={this.logs}/>
    );
  }

}
