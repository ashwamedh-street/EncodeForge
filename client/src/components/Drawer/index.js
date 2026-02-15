import React, { useContext } from 'react';
import clsx from 'clsx';
import { makeStyles, useTheme } from '@material-ui/core/styles';
import Drawer from '@material-ui/core/Drawer';
import CssBaseline from '@material-ui/core/CssBaseline';
import AppBar from '@material-ui/core/AppBar';
import Toolbar from '@material-ui/core/Toolbar';
import List from '@material-ui/core/List';
import Typography from '@material-ui/core/Typography';
import Divider from '@material-ui/core/Divider';
import IconButton from '@material-ui/core/IconButton';
import MenuIcon from '@material-ui/icons/Menu';
import ChevronLeftIcon from '@material-ui/icons/ChevronLeft';
import ChevronRightIcon from '@material-ui/icons/ChevronRight';
import ListItem from '@material-ui/core/ListItem';
import { ListItemText } from '@material-ui/core';
import Activity from '../Activity'
import ActivityTeam from '../ActivityTeam';
import { BrowserRouter, Route, Link } from 'react-router-dom';
import AuthUserContext from '../../Contexts/AuthUserContext';
import Home  from '../Home';

const drawerWidth = 240;

// A menu for browser that routes to specific components
const minimumRequiredYear = 2;
const DrawerMenu = [
    {
      text : 'Activity',
    },
    {
      text : 'Requests',
      year : minimumRequiredYear,
    },
    {
      text : 'Stats',
      year : minimumRequiredYear,
    },
]
const DrawerMenuTeam = [
  {
    text : 'Activity',
  },
  {
    text : 'Requests',
    year : minimumRequiredYear,
  },
  {
    text : 'Stats',
    year : minimumRequiredYear,
  },
]

const useStyles = makeStyles((theme) => ({
  root: {
    display: 'flex',
  },
  appBar: {
    transition: theme.transitions.create(['margin', 'width'], {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.leavingScreen,
    }),
  },
  appBarShift: {
    width: `calc(100% - ${drawerWidth}px)`,
    marginLeft: drawerWidth,
    transition: theme.transitions.create(['margin', 'width'], {
      easing: theme.transitions.easing.easeOut,
      duration: theme.transitions.duration.enteringScreen,
    }),
  },
  menuButton: {
    marginRight: theme.spacing(2),
  },
  hide: {
    display: 'none',
  },
  drawer: {
    width: drawerWidth,
    flexShrink: 0,
  },
  drawerPaper: {
    width: drawerWidth,
  },
  drawerHeader: {
    display: 'flex',
    alignItems: 'center',
    padding: theme.spacing(0, 1),
    // necessary for content to be below app bar
    ...theme.mixins.toolbar,
    justifyContent: 'flex-end',
  },
  content: {
    flexGrow: 1,
    padding: theme.spacing(3),
    transition: theme.transitions.create('margin', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.leavingScreen,
    }),
    marginLeft: -drawerWidth,
  },
  contentShift: {
    transition: theme.transitions.create('margin', {
      easing: theme.transitions.easing.easeOut,
      duration: theme.transitions.duration.enteringScreen,
    }),
    marginLeft: 0,
  },
}));

export default function PersistentDrawerLeft() {

  const classes = useStyles();
  const theme = useTheme();
  const [open, setOpen] = React.useState(false);
  const authUser = useContext(AuthUserContext);

  const handleDrawerOpen = () => {
    setOpen(true);
  };

  const handleDrawerClose = () => {
    setOpen(false);
  };

  return (
    <BrowserRouter>
    <div className={classes.root}>
      <CssBaseline />
      <AppBar
        position="fixed"
        className={clsx(classes.appBar, {
          [classes.appBarShift]: open,
        })}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            onClick={handleDrawerOpen}
            edge="start"
            className={clsx(classes.menuButton, open && classes.hide)}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" color = "initial"> 
            Ashwamedh
          </Typography>
        </Toolbar>
      </AppBar>
       {/* *********************************************************************************************************  */}
      <Drawer
        className={classes.drawer} variant="persistent" anchor="left" open={open}
        classes={{
          paper: classes.drawerPaper,
        }}
      >
        <div className={classes.drawerHeader}>
          <IconButton onClick={handleDrawerClose}>
            {theme.direction === 'ltr' ? <ChevronLeftIcon /> : <ChevronRightIcon />}
          </IconButton>
        </div>
        <Divider />

        <List>
          {DrawerMenu.map((e) => (
            <ListItem button key={e.text} component = {Link} to = {"/home/" + e.text.toLowerCase()}>
              <ListItemText primary={e.text} />
            </ListItem>
          ))}
        </List>
        <Divider />
        <List>
          {DrawerMenuTeam.map((e) => (
            <ListItem button key={e.text} component = {Link} to = {"/home/team/" + e.text.toLowerCase()}>
              <ListItemText primary={e.text} />
            </ListItem>
          ))}
        </List>
            
      </Drawer>

      {/* *********************************************************************************************************** */}
      <main
        className={clsx(classes.content, {
          [classes.contentShift]: open,
        })}
      >
        <div className={classes.drawerHeader} />
            <Route exact path = "/home" render = { () => <Home/>}/>
            <Route exact path = "/home/activity" render = { () => <Activity/>}/>
            {/* ************************************************************ */}
            <Route exact path = "/home/team/activity" render = { () => <ActivityTeam/>}/>

      </main>
    </div>
    </BrowserRouter>
  );
}
