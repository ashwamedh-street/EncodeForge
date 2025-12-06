import React, { Component, useState } from "react";
import { makeStyles } from '@material-ui/core/styles';
import Button from '@material-ui/core/Button';
import Modal from '@material-ui/core/Modal';
import { TextField } from "@material-ui/core";
import { withStyles } from "@material-ui/styles";
import axios from 'axios'
import AddIcon from '@material-ui/icons/Add';
import { Fab } from "@material-ui/core";
import AuthUserContext from "../../../Contexts/AuthUserContext";
import CommentBox from './Comments/CommentBox'
import DoneIcon from '@material-ui/icons/Done';
import Done from "@material-ui/icons/Done";
import { IconButton } from "@material-ui/core";
import CloseIcon from '@material-ui/icons/Close';
import RefreshContext from "../../../Contexts/RefreshContext";


function getModalStyle() {
const top = 50 
const left = 50 
return {
    top: `${top}%`,
    left: `${left}%`,
    transform: `translate(-${top}%, -${left}%)`,
    display:'flex',
    flexDirection:'column',
    alignItems:'center',  
    justifyContent:'center',
};
}

const useStyles = theme => ({
    modal: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
    },
    paper: {
        position: 'absolute',
        width: 800,
        backgroundColor: 'white',
        padding:'20px',
        height:'20%',
        border:'1px black solid '
    },
    button:{
        backgroundColor: '#99ccff',
        borderRadius: 3,
        border: 0,
        color:'white',
        textTransform: 'none',
        fontFamily:'Lato',
    },
    iconButton:{
        display: "flex",
        flexDirection: "column"
    }
});

class SimpleModal extends Component{
constructor(props){
    super(props);
    this.state = {
        open:false,
        value:'',
        verdict: 'toBeDecided',
    }
}
render(){
    const {data} = this.props;
    const {classes} = this.props;
    const modalStyle = getModalStyle();

    const handleOpen = () => {
        this.setState({open:true});
    };

    const handleClose = () => {
        this.setState({open:false});
    };

    const onSubmit = (e) =>{
        e.preventDefault()
        handleClose();
        const authUser = this.context.authUser
        
        if(!authUser) return 
        // const setAuthUser = this.context.setAuthUser
        const send = {
            verdict:this.state.verdict,
            text:this.state.value,
            activityUserId:data.userId,
            activityId:data._id,
            approverId:authUser.uid,
            //_id is activity id
        }
        console.log(send,'sending to approve');
        const url = `/api/${authUser.uid}/processActivity`;
        axios.post(url,send).then((res)=>{
            
        });
    }
    return (
        <div>
        {
            <Button variant="contained" size="small" className={classes.button} onClick = {handleOpen}>
                Process
            </Button>
        }
            <Modal
                aria-labelledby="simple-modal-title"
                aria-describedby="simple-modal-description"
                open={this.state.open}
                onClose={handleClose}
            >
                <div style={modalStyle} className={classes.paper} >

                    <h2>Process Activity</h2>
                    <form onSubmit = {onSubmit} style = {{width:'100%',display:'flex',justifyContent:'center',marginBottom:'20px',alignItems:'center'}}>
                        {/* do we need to have an 'Add Remark' option here. */}
                        
                        {/* <TextField
                            id="outlined-textarea"
                            label="Remark"
                            // placeholder="Say something about this activit"
                            multiline
                            variant="outlined"
                            value = {this.state.value}
                            onChange = {(e)=>this.setState({value:e.target.value})}
                            style = {{width:'80%'}}
                            size = 'small'
                        />
                        <IconButton color="primary" aria-label="approve" type = "submit" onClick = {addRemark} >
                            <AddIcon />
                        </IconButton> */}
                        <Button color="secondary" variant = 'outlined'  aria-label="approve" type = "submit" onClick = {()=>{this.setState({verdict:'deny'})}} disabled = {data.status !== 'Pending'}>
                            Deny
                        </Button>
                        <Button color="primary" variant = 'outlined' aria-label="approve" type = "submit" onClick = {()=>{this.setState({verdict:'approve'})}} disabled = {data.status !== 'Pending'}>
                            Approve
                        </Button>
                    </form>
                </div>
            </Modal>
        </div>
    );
}
}
SimpleModal.contextType = AuthUserContext;
export default withStyles(useStyles)(SimpleModal)