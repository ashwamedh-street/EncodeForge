import React, { Component } from "react";
import { makeStyles } from '@material-ui/core/styles';
import Button from '@material-ui/core/Button';
import Modal from '@material-ui/core/Modal';
import { TextField } from "@material-ui/core";
import { withStyles } from "@material-ui/styles";
import axios from 'axios'
import AuthUserContext from '../../Contexts/AuthUserContext'
import AddIcon from '@material-ui/icons/Add';
import { Fab } from "@material-ui/core";
import { CircularProgress,Input,FormControl,InputLabel,Select,MenuItem} from "@material-ui/core";
// import Loader from './Loader'

function getModalStyle() {
const top = 50
const left = 50
return {
    top: `${top}%`,
    left: `${left}%`,
    transform: `translate(-${top}%, -${left}%)`,
    height:'200px',
    display:'flex',
    alignItems:'center',
    justifyContent:'space-between',
};
}

function getFormStyle()
{
       return {
        display: 'inline-flex',
        flexDirection: 'row',
        flexWrap:'wrap',
        justifyContent: 'space-between',
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
    },
});

class SimpleModal extends Component{
constructor(props){
    super(props);
    this.state = {
        open:false,
        title:'',
        description:'',
        category:'',
        // isLoading:!props.isTableReady,
    }
}
render(){
    const {classes} = this.props;
    const modalStyle = getModalStyle();
    const formStyle=getFormStyle();
    const handleOpen = () => {
        this.setState({open:true});
    };

    const handleClose = () => {
        this.setState({open:false});
    };

    const handleOnChange = (e) =>{
        this.setState({[e.target.name]:e.target.value})
    }
    const onSubmit = (e) =>{
        e.preventDefault()
        handleClose();
        const authUser = this.context.authUser
        const setAuthUser = this.context.setAuthUser

        const send = {
            title:this.state.title,
            description:this.state.description,
            category:this.state.category,
            status:'Pending',
            dateIssued: new Date(),
        }
        console.log(send,'sending this to backend');

        const url = `/api/${authUser.uid}/addWork`
        //updates and fetches new data from the backend
        //the received data is set in the context which leads to rerendering of depenedent components
        axios.post(url,{
            work:[send]
        }).then((authUser)=>{
            setAuthUser(authUser.data);
            this.props.refreshTable();
        }).then( this.setState({isLoading:false}))

    }

    return (
        <div>
        {
            <Fab size = "medium" color="primary" clasaName = {classes.root} onClick = {handleOpen} disabled = {this.state.isLoading}>
            <AddIcon />
            </Fab>
        }
            <Modal
                aria-labelledby="simple-modal-title"
                aria-describedby="simple-modal-description"
                open={this.state.open}
                onClose={handleClose}
            >
                <div style={modalStyle} className={classes.paper}>
                    <h2>Add Activity</h2>
                    <form onSubmit = {onSubmit}>
                <div style={formStyle}>
                <TextField required id="outlined-title" label="title" name = "title" type="text" onChange={handleOnChange} required="true" variant="outlined" />
                <TextField required id="outlined-desc" label="description" name = "description" type="text" onChange={handleOnChange} required="true" variant="outlined" />
                <FormControl variant="outlined" className={classes.formControl}>
                    <InputLabel id="outlined-category-label">Category</InputLabel>
                    <Select onChange={handleOnChange} label="category" required="true" name = "category" value="Category">
                        <MenuItem value="None">
                        <em>None</em>
                        </MenuItem>
                        <MenuItem value="Script" ><em>Script</em></MenuItem>
                        <MenuItem value="Online"><em>Online</em></MenuItem>
                    </Select>
                    </FormControl>
                    {/* <Button variant="contained" color="primary" type = "submit">Submit</Button> */}
                    <label>
                            <input type = 'submit'/>
                        </label>
                    </div>
                    </form>
                    </div>
            </Modal>
        </div>
    );
}
}
SimpleModal.contextType = AuthUserContext;
export default withStyles(useStyles)(SimpleModal)
