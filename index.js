const express = require('express');
const app = express();
const mongoose = require('mongoose')
const keys = require('./config/keys')
const ApiRoutes = require('./routes/ApiRoutes');
require('./models/User')
const User = mongoose.model('users');

//connect to database via mongoose
mongoose.connect(keys.mongoURI,{
    useNewUrlParser: true,
    useCreateIndex: true,
    useUnifiedTopology: true
});

app.use(express.json())

app.use('/api',ApiRoutes);



const PORT = process.env.port | 5000
app.listen(PORT)
console.log(`running on port ${PORT}`)

