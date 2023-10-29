<template>

<div>
  <!-- Create Feature Form Modal -->
  <a-modal v-model:visible="isShowCreateFeatureModal" width="1000px" :title="$t('Create Feature View')" >
    <template #footer>
      <a-button @click="handleCancel">Cancel</a-button>
    </template>
    <CreateFeatureViewForm @submitted="submittedCreateFeatureForm"></CreateFeatureViewForm>
  </a-modal>


  <a-typography-paragraph>
    <pre>{{ $t("Text of introduce deploy feature service") }} <a target="blank" href="https://openmldb.ai/docs/zh/main/openmldb_sql/deployment_manage/DEPLOY_STATEMENT.html">{{$t('OpenMLDB documents')}}</a></pre>
  </a-typography-paragraph>
  <br/>

  <!-- Create form -->
  <a-form
    :model="formState"
    layout="vertical"
    @submit="submitForm">

    <a-form-item
      :label="$t('Feature Service Name')"
      :rules="[{ required: true, message: 'Please input name!' }]">

      <a-select show-search @search="updateServiceName" id="itemSelect" v-model:value="formState.name" @change="updateSelectedService">
          <option v-for="featureService in featureServices" :value="featureService.name">{{ featureService.name }}</option>
      </a-select>
    </a-form-item>

    <a-form-item
      :label="$t('Feature Service Version')"
      :rules="[{ required: true, message: 'Please input version!' }]"
      @blur="checkServiceVersion">
      <a-input v-model:value="formState.version" />
    </a-form-item>

    <a-form-item
      :label="$t('Feature Collection')"
      :rules="[{ required: true, message: 'Please input feature list!' }]">

      <a-button type="primary" @click="clickCreateFeature">{{ $t('Create Feature') }}</a-button>
      
      <br/><br/>
      <a-select mode="multiple" v-model:value="formState.featureSet">
          <option v-for="featureOption in featureOptions" :value="featureOption">{{ featureOption }}</option>
      </a-select>
    </a-form-item>

    <a-form-item 
      :label="$t('Main Table Keys')">
      <a-tooltip>
        <template #title>{{$t('Text of introduce join keys')}}</template>
        <a-input id="mainTableKeys" v-model:value="formState.mainTableKeys"></a-input>
      </a-tooltip>
    </a-form-item>

    <a-form-item
      :label="$t('Feature Service Description')">
      <a-input v-model:value="formState.description" />
    </a-form-item>
  </a-form>

</div>
</template>
  
<script>
import axios from 'axios'
import { message } from 'ant-design-vue';
import CreateFeatureViewForm from '@/components/form/CreateFeatureViewForm.vue'

export default {
  components: {
    CreateFeatureViewForm
  },

  data() {
    return {
      isShowCreateFeatureModal: false,

      featureOptions: [],

      featureServices: [],
      featureServiceVersions: [],

      formState: {
        name: '',
        version: '',
        featureSet: [],
        mainTableKeys: "",
        description: '',
      },

    };
  },

  mounted() {
    this.initData();
  },

  methods: {
    initData() {
      axios.get(`/api/featureservices/latest`)
        .then(response => {
          this.featureServices = response.data;
        })
        .catch(error => {
          message.error(error.message);
        })
        .finally(() => {});

      this.updateSelectFeatureOptions();
    },

    updateSelectFeatureOptions() {
      axios.get(`/api/featureviews`)
        .then(response => {
          response.data.forEach(featureView => {
            // Append feature view name
            this.featureOptions.push(featureView.name)
          });
        })
        .catch(error => {
          message.error(error.message);
        })
        .finally(() => {});

      axios.get(`/api/features`)
        .then(response => {
          response.data.forEach(feature => {
            // Append complete feature name
            const completeFeatureName = feature.featureViewName + ":" + feature.featureName;
            this.featureOptions.push(completeFeatureName);
          });
        })
        .catch(error => {
          message.error(error.message);
        })
        .finally(() => {});

      if (this.$route.query.featureview != null) {
        this.formState.featureSet = [this.$route.query.featureview];
      }


      if (this.$route.query.featureservice != null) {
        // Url provides feature service name
        this.formState.name = this.$route.query.featureservice;
        if (this.$route.query.version != null) {
          this.formState.version = this.$route.query.version;
        }
        this.updateSelectedService();
      }

    },

    submitForm() {
      axios.post(`/api/featureservices`, {
        "name": this.formState.name,
        "version": this.formState.version,
        "featureNames": this.formState.featureSet.join(','),
        "description": this.formState.description,
        "mainTableKeys": this.formState.mainTableKeys
      })
      .then(response => {
        message.success(`Success to add feature service ${this.formState.name} and version ${this.formState.version}`);

        // Redirect to FeatureView detail page
        this.$router.push(`/featureservices/${this.formState.name}/${this.formState.version}/result`);
      })
      .catch(error => {
          if (error.response.data) {
            message.error(error.response.data);
          } else {
            message.error(error.message);
          }
      });
    },

    updateSelectedService() {
      if (this.formState.name != "") {
        axios.get(`/api/featureservices/${this.formState.name}/versions`)
          .then(response => {
            this.featureServiceVersions = response.data;
            this.featureServiceVersions.push("");
          })
          .catch(error => {
            message.error(error.message);
          });  
      }
    },

    updateServiceName(value){
      if (value){
       this.formState.name=value
      }
    },

    checkServiceVersion(){
      if (this.test.includes(this.formState.version)){
         message.error("Service version already exists, please rename.");
         this.formState.version = ''
      } 
    },

    clickCreateFeature() {
      this.isShowCreateFeatureModal = true;
    },

    handleCancel() {
      this.isShowCreateFeatureModal = false;
    },

    submittedCreateFeatureForm(newFeatureViewName) {
      this.isShowCreateFeatureModal = false;
      this.updateSelectFeatureOptions();
      this.formState.featureSet = [newFeatureViewName]
    }

  },
};
</script>