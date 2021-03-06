import {createAsyncThunk, createSlice} from "@reduxjs/toolkit";
import API from "../../../api/Api";

const initialState = {
    url: '',
    entities: [],
    ontologyStructure: [],
    editRow: []
}


export const getAllEntities = createAsyncThunk('listResources/getAllEntitiesInfo', async () => {
    return API.GET("getAllEntitiesInfo", '', [] ).then(res => {
    })
})


export const getOntologyStructureInfo = createAsyncThunk('listResources/getOntologyStructureInfo', async () => {
    return API.GET("getOntologyStructureInfo", '', [] ).then(res => {
        const ontologyStructure = res.data
        console.log(ontologyStructure)
        return { ontologyStructure }
    })
})


export const editInstance = createAsyncThunk('listResources/editInstance', async (form) => {
    return API.POST("editInstance", '', form ).then(res => {
        return res
    })
})


export const editResourceSingleEndpoint = createAsyncThunk('listResources/editResourceSingleEndpoint', async (form) => {
    return API.POST("editResourceSingleEndpoint", '', form ).then(res => {
        return res
    })
})


export const editResourceOMIMEndpoint = createAsyncThunk('listResources/editResourceOMIMEndpoint', async (form) => {
    return API.POST("editResourceOMIMEndpoint", '', form ).then(res => {
        return res
    })
})


export const removeInstance = createAsyncThunk('listResources/removeInstance', async (form) => {
    return API.POST("removeInstance", '', form ).then(res => {
        return res.data
    })
})



const listResourceSlice = createSlice({
    name: 'listResources',
    initialState,
    reducers: {
        storeEditRow: (state, action) => {
            if (action.payload.typeOf === "Entity") {
                let isEntityOf_replace = []
                action.payload.isEntityOf.map((key) => { isEntityOf_replace.push(key.label) })
                state.editRow = Object.assign({isEntityOf_replace: isEntityOf_replace}, action.payload);
            }
            else if (action.payload.typeOf === "Concept") {
                let relatedResourceLabel = []
                action.payload.hasResource.map((key) => { relatedResourceLabel.push(key.label) })
                state.editRow = Object.assign({relatedResourceLabel: relatedResourceLabel}, action.payload);
                state.editRow = Object.assign({extendedEntityLabel: action.payload.hasEntity.replace("http://bioinformatics.ua.pt/diseasecard/resource/","")}, state.editRow);
            }
            else if (action.payload.typeOf === "Resource") {
                if (action.payload.publisher === "plugin") { state.editRow = Object.assign({publisher: "OMIM"}, action.payload);}
                else {                                       state.editRow = Object.assign({publisher: action.payload.publisher.toUpperCase()}, action.payload);}
            }


        }
    },
    extraReducers: {
        [getAllEntities.pending]: (state, action) => {
            /*state.status = 'loading'*/
        },
        [getAllEntities.fulfilled]: (state, action) => {
            /*state.invalidEndpoints = action.payload.invalidEndpoints
            state.url = action.payload.url
            state.protection = action.payload.protection
            state.status = 'succeeded'*/
        },
        [getAllEntities.rejected]: (state, action) => {
            /*state.status = 'failed'
            state.omim = action.meta.arg
            state.error = action.error.message*/
        },
        [getOntologyStructureInfo.fulfilled]: (state, action) => {
            //console.log(action)
            state.ontologyStructure = action.payload.ontologyStructure
        },
    }
})


export const getOntologyStructure = state => state.listResources.ontologyStructure
export const getEditRow = state => state.listResources.editRow

export const { storeEditRow } = listResourceSlice.actions

export default listResourceSlice.reducer
